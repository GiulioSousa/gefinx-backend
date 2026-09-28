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
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saldo nunca negativo, em dia algum (Etapa 26), contra o banco de verdade: a conta dia a dia
 * é uma soma por janela em SQL nativo, e é aqui que ela prova somar certo.
 *
 * <p>O cenário começa com R$ 100 recebidos no Banco em 10/01/2026, e a Carteira vazia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaldoNaoNegativoTest {

    private static final int CRIADO = 201;
    private static final int OK = 200;
    private static final int CONFLITO = 409;

    private static final String PREFIXO = "saldo-nao-negativo-";

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
    private long categoriaReceita;
    private long categoriaDespesa;
    private long receita;

    @BeforeEach
    void prepararCenario() {
        token = jwtService.gerarToken(ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime()));
        banco = extrairId(enviar("POST", "/api/contas", "{\"nome\":\"Banco\",\"saldoInicial\":0}").body());
        carteira = extrairId(enviar("POST", "/api/contas", "{\"nome\":\"Carteira\",\"saldoInicial\":0}").body());
        categoriaReceita = extrairId(enviar("POST", "/api/categorias", "{\"nome\":\"Trabalho\",\"tipo\":\"RECEITA\"}").body());
        categoriaDespesa = extrairId(enviar("POST", "/api/categorias", "{\"nome\":\"Mercado\",\"tipo\":\"DESPESA\"}").body());

        HttpResponse<String> resposta = lancar("RECEITA", categoriaReceita, banco, "100.00", "2026-01-10");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        receita = extrairId(resposta.body());
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void despesaMaiorQueOSaldoERecusadaENadaEGravado() {
        HttpResponse<String> resposta = lancar("DESPESA", categoriaDespesa, banco, "150.00", "2026-01-20");

        assertThat(resposta.statusCode()).isEqualTo(CONFLITO);
        assertThat(mensagem(resposta)).contains("Banco").contains("20/01/2026");
        assertThat(contarTransacoes()).isEqualTo(1);
    }

    /** O saldo final fecharia positivo, mas no dia 5 o dinheiro ainda não tinha entrado. */
    @Test
    void despesaComDataAnteriorAoDinheiroERecusada() {
        HttpResponse<String> resposta = lancar("DESPESA", categoriaDespesa, banco, "50.00", "2026-01-05");

        assertThat(resposta.statusCode()).isEqualTo(CONFLITO);
        assertThat(mensagem(resposta)).contains("05/01/2026");
    }

    /** Vale o saldo no fim do dia: receber e pagar no mesmo dia, até zerar, é permitido. */
    @Test
    void zerarAContaNoMesmoDiaEmQueODinheiroEntrouEPermitido() {
        assertThat(lancar("DESPESA", categoriaDespesa, banco, "100.00", "2026-01-10").statusCode()).isEqualTo(CRIADO);
    }

    @Test
    void transferenciaSemSaldoNaOrigemERecusada() {
        assertThat(transferir("200.00").statusCode()).isEqualTo(CONFLITO);
        assertThat(transferir("100.00").statusCode()).isEqualTo(CRIADO);
    }

    @Test
    void reduzirAReceitaAbaixoDasDespesasERecusado() {
        lancar("DESPESA", categoriaDespesa, banco, "80.00", "2026-01-20");

        HttpResponse<String> edicao = enviar("PUT", "/api/transacoes/" + receita, corpo(
            "RECEITA", categoriaReceita, banco, "50.00", "2026-01-10"));

        assertThat(edicao.statusCode()).isEqualTo(CONFLITO);
        assertThat(jdbcTemplate.queryForObject("SELECT valor FROM transacoes WHERE id = ?", String.class, receita))
            .as("a recusa desfaz a gravação")
            .isEqualTo("100.00");
    }

    @Test
    void excluirAReceitaQueSustentaUmaDespesaERecusado() {
        lancar("DESPESA", categoriaDespesa, banco, "80.00", "2026-01-20");

        assertThat(enviar("DELETE", "/api/transacoes/" + receita, null).statusCode()).isEqualTo(CONFLITO);
        assertThat(contarTransacoes()).isEqualTo(2);
    }

    @Test
    void moverUmaDespesaParaUmaContaSemSaldoERecusado() {
        long despesa = extrairId(lancar("DESPESA", categoriaDespesa, banco, "80.00", "2026-01-20").body());

        HttpResponse<String> edicao = enviar("PUT", "/api/transacoes/" + despesa, corpo(
            "DESPESA", categoriaDespesa, carteira, "80.00", "2026-01-20"));

        assertThat(edicao.statusCode()).isEqualTo(CONFLITO);
        assertThat(mensagem(edicao)).contains("Carteira");
    }

    /** O pagamento passa pelo mesmo serviço, e a recusa desfaz também a marca de paga. */
    @Test
    void pagarDespesaPlanejadaSemSaldoERecusadoEElaContinuaPendente() {
        long despesa = extrairId(enviar("POST", "/api/despesas-planejadas",
            "{\"descricao\":\"Aluguel\",\"valor\":500.00,\"prazo\":\"2026-02-10\"}").body());

        HttpResponse<String> pagamento = enviar("POST", "/api/despesas-planejadas/" + despesa + "/pagamento",
            "{\"descricao\":\"Aluguel\",\"valor\":500.00,\"dataTransacao\":\"2026-02-01\",\"categoriaId\":"
                + categoriaDespesa + ",\"contaId\":" + banco + "}");

        assertThat(pagamento.statusCode()).isEqualTo(CONFLITO);
        assertThat(contarTransacoes()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT transacao_id IS NULL FROM despesas_planejadas WHERE id = ?", Boolean.class, despesa
        )).isTrue();
    }

    @Test
    void oSaldoDasContasEOConsolidadoContinuamNaoNegativos() {
        lancar("DESPESA", categoriaDespesa, banco, "150.00", "2026-01-20");
        transferir("200.00");

        HttpResponse<String> contas = enviar("GET", "/api/contas", null);
        assertThat(contas.statusCode()).isEqualTo(OK);
        objectMapper.readTree(contas.body())
            .forEach(conta -> assertThat(conta.get("saldo").decimalValue()).isNotNegative());
        assertThat(objectMapper.readTree(enviar("GET", "/api/saldo", null).body()).get("saldo").decimalValue())
            .isEqualByComparingTo("100.00");
    }

    private HttpResponse<String> lancar(String tipo, long categoriaId, long contaId, String valor, String data) {
        return enviar("POST", "/api/transacoes", corpo(tipo, categoriaId, contaId, valor, data));
    }

    private HttpResponse<String> transferir(String valor) {
        return enviar("POST", "/api/transacoes",
            "{\"descricao\":\"Saque\",\"valor\":" + valor + ",\"tipo\":\"TRANSFERENCIA\",\"contaId\":" + banco
                + ",\"contaDestinoId\":" + carteira + ",\"dataTransacao\":\"2026-01-15\"}");
    }

    private static String corpo(String tipo, long categoriaId, long contaId, String valor, String data) {
        return "{\"descricao\":\"" + tipo + " " + data + "\",\"valor\":" + valor + ",\"tipo\":\"" + tipo
            + "\",\"categoriaId\":" + categoriaId + ",\"contaId\":" + contaId + ",\"dataTransacao\":\"" + data + "\"}";
    }

    private long contarTransacoes() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transacoes t JOIN usuarios u ON u.id = t.usuario_id WHERE u.usuario LIKE ?",
            Long.class, PREFIXO + "%"
        );
    }

    private String mensagem(HttpResponse<String> resposta) {
        return objectMapper.readTree(resposta.body()).get("mensagem").asString();
    }

    private long extrairId(String corpo) {
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String corpo) {
        HttpRequest.BodyPublisher publisher = corpo == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(corpo);

        HttpRequest requisicao = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .method(metodo, publisher)
            .build();

        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar " + caminho, excecao);
        }
    }
}
