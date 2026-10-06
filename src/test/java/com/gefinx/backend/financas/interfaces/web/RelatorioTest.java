package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import com.gefinx.backend.usuarios.infraestrutura.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O relatório mensal contra o banco de verdade: o agrupamento por mês usa {@code extract} no
 * JPQL, e o que pode dar errado nele — o tipo que o banco devolve, o mês nas pontas — só aparece
 * no SQL de fato executado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RelatorioTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int REQUISICAO_INVALIDA = 400;

    private static final String PREFIXO = "relatorio-";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int porta;

    private String token;
    private long banco;
    private long carteira;

    /**
     * Antes do período, 1.000 de receita: é o saldo com que agosto começa. Agosto termina no
     * último dia com uma despesa; setembro começa no primeiro com uma receita, e tem uma
     * transferência que não pode virar nem receita nem despesa. Outubro fica vazio.
     */
    @BeforeEach
    void prepararCenario() {
        token = criarUsuarioEObterToken();
        banco = criarConta("Banco");
        carteira = criarConta("Carteira");
        long trabalho = criarCategoria("Trabalho", "RECEITA");
        long mercado = criarCategoria("Mercado", "DESPESA");
        long lazer = criarCategoria("Lazer", "DESPESA");

        lancar("RECEITA", trabalho, "1000.00", "2026-07-20");
        lancar("DESPESA", mercado, "100.00", "2026-08-31");
        lancar("RECEITA", trabalho, "500.00", "2026-09-01");
        lancar("DESPESA", lazer, "50.00", "2026-09-20");
        lancar("DESPESA", mercado, "200.00", "2026-09-15");
        transferir("300.00", "2026-09-10");
        lancar("DESPESA", mercado, "70.00", "2026-11-01");
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void umaLinhaPorMesComOSaldoAcumulado() {
        JsonNode meses = obter("/api/relatorios/mensal?inicio=2026-08&fim=2026-11");

        assertThat(meses).hasSize(4);
        confere(meses.get(0), "2026-08", "0", "100.00", "-100.00", "900.00");
        confere(meses.get(1), "2026-09", "500.00", "250.00", "250.00", "1150.00");
        assertThat(meses.get(1).get("despesas").decimalValue())
            .as("a transferência de 10/09 não é gasto").isEqualByComparingTo("250.00");
        confere(meses.get(2), "2026-10", "0", "0", "0", "1150.00");
        confere(meses.get(3), "2026-11", "0", "70.00", "-70.00", "1080.00");
    }

    @Test
    void oSaldoDoUltimoMesEOSaldoAteOFimDele() {
        JsonNode meses = obter("/api/relatorios/mensal?inicio=2026-08&fim=2026-11");
        JsonNode saldo = obter("/api/saldo?dataFim=2026-11-30");

        assertThat(meses.get(3).get("saldoAoFim").decimalValue()).isEqualByComparingTo(saldo.get("saldo").decimalValue());
    }

    @Test
    void asCategoriasDoMesVemDoMaiorParaOMenor() {
        JsonNode despesas = obter("/api/relatorios/categorias?mes=2026-09");
        assertThat(despesas).hasSize(2);
        assertThat(despesas.get(0).get("nome").asString()).isEqualTo("Mercado");
        assertThat(despesas.get(0).get("total").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(despesas.get(1).get("nome").asString()).isEqualTo("Lazer");

        JsonNode receitas = obter("/api/relatorios/categorias?mes=2026-09&tipo=RECEITA");
        assertThat(receitas).hasSize(1);
        assertThat(receitas.get(0).get("total").decimalValue()).isEqualByComparingTo("500.00");

        assertThat(obter("/api/relatorios/categorias?mes=2026-10")).isEmpty();
    }

    @Test
    void outroUsuarioNaoVeNadaDisso() {
        String outro = criarUsuarioEObterToken();

        JsonNode meses = obter("/api/relatorios/mensal?inicio=2026-08&fim=2026-11", outro);

        assertThat(meses).hasSize(4);
        assertThat(meses.get(3).get("saldoAoFim").decimalValue()).isEqualByComparingTo("0");
        assertThat(obter("/api/relatorios/categorias?mes=2026-09", outro)).isEmpty();
    }

    @Test
    void recusaPedidosQueORelatorioNaoMonta() {
        assertThat(status("/api/relatorios/mensal?inicio=2026-11&fim=2026-08")).as("invertido").isEqualTo(REQUISICAO_INVALIDA);
        assertThat(status("/api/relatorios/mensal?inicio=2024-11&fim=2026-11")).as("25 meses").isEqualTo(REQUISICAO_INVALIDA);
        assertThat(status("/api/relatorios/mensal?inicio=2024-12&fim=2026-11")).as("24 meses").isEqualTo(OK);
        assertThat(status("/api/relatorios/mensal?inicio=2026-13&fim=2026-11")).as("mês inexistente").isEqualTo(REQUISICAO_INVALIDA);
        assertThat(status("/api/relatorios/categorias?mes=2026-09&tipo=TRANSFERENCIA")).isEqualTo(REQUISICAO_INVALIDA);
    }

    private void confere(JsonNode mes, String nome, String receitas, String despesas, String resultado, String saldoAoFim) {
        assertThat(mes.get("mes").asString()).isEqualTo(nome);
        assertThat(mes.get("receitas").decimalValue()).as("%s receitas", nome).isEqualByComparingTo(receitas);
        assertThat(mes.get("despesas").decimalValue()).as("%s despesas", nome).isEqualByComparingTo(despesas);
        assertThat(mes.get("resultado").decimalValue()).as("%s resultado", nome).isEqualByComparingTo(resultado);
        assertThat(mes.get("saldoAoFim").decimalValue()).as("%s saldo", nome).isEqualByComparingTo(saldoAoFim);
    }

    private int status(String caminho) {
        return enviar("GET", caminho, token, null).statusCode();
    }

    private JsonNode obter(String caminho) {
        return obter(caminho, token);
    }

    private JsonNode obter(String caminho, String tokenUsado) {
        HttpResponse<String> resposta = enviar("GET", caminho, tokenUsado, null);
        assertThat(resposta.statusCode()).as("GET %s: %s", caminho, resposta.body()).isEqualTo(OK);
        return objectMapper.readTree(resposta.body());
    }

    private void lancar(String tipo, long categoriaId, String valor, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"" + tipo + " " + data + "\",\"valor\":" + valor + ",\"tipo\":\"" + tipo
                + "\",\"categoriaId\":" + categoriaId + ",\"contaId\":" + banco
                + ",\"dataTransacao\":\"" + data + "\"}");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
    }

    private void transferir(String valor, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Saque\",\"valor\":" + valor + ",\"tipo\":\"TRANSFERENCIA\",\"contaId\":" + banco
                + ",\"contaDestinoId\":" + carteira + ",\"dataTransacao\":\"" + data + "\"}");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
    }

    private long criarConta(String nome) {
        HttpResponse<String> resposta = enviar("POST", "/api/contas", token, "{\"nome\":\"" + nome + "\",\"saldoInicial\":0}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private long criarCategoria(String nome, String tipo) {
        HttpResponse<String> resposta = enviar("POST", "/api/categorias", token,
            "{\"nome\":\"" + nome + "\",\"tipo\":\"" + tipo + "\"}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    /** Ver {@link ListagemPaginadaTest}: o token é assinado aqui para não esgotar o limite do login. */
    private String criarUsuarioEObterToken() {
        return jwtService.gerarToken(ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime()));
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String tokenUsado, String corpo) {
        HttpRequest.BodyPublisher publisher = corpo == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(corpo);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .method(metodo, publisher);

        if (tokenUsado != null) {
            builder.header("Authorization", "Bearer " + tokenUsado);
        }

        try {
            return clienteHttp.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar " + caminho, excecao);
        }
    }
}
