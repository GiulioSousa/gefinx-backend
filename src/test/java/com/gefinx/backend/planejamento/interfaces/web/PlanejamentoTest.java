package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
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
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O planejamento contra o banco de verdade: a migration V11, a chave composta das contas de
 * fora, e o saldo lido de finanças pela porta.
 *
 * <p>Os lançamentos ficam dez dias atrás, e não ontem, para que o saldo de ontem e o de hoje
 * sejam os mesmos até o teste lançar algo com a data de hoje de propósito. "Hoje" é o do fuso
 * configurado — o mesmo que o servidor usa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanejamentoTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int REQUISICAO_INVALIDA = 400;
    private static final int NAO_ENCONTRADO = 404;

    private static final String PREFIXO = "planejamento-";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private final LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

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
    private long especie;
    private long categoriaReceita;

    @BeforeEach
    void prepararCenario() {
        token = criarUsuarioEObterToken();
        banco = criarConta(token, "Banco");
        especie = criarConta(token, "Em espécie");
        categoriaReceita = extrairId(enviar("POST", "/api/categorias", token,
            "{\"nome\":\"Trabalho\",\"tipo\":\"RECEITA\"}").body());

        receber(banco, "1000.00", hoje.minusDays(10));
        receber(especie, "300.00", hoje.minusDays(10));
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void semContasDeForaOSaldoEOConsolidado() {
        receber(banco, "40.00", hoje);

        JsonNode plano = obter("/api/planejamento");

        assertThat(plano.get("hoje").asString()).isEqualTo(hoje.toString());
        assertThat(plano.get("saldoDeOntem").decimalValue()).isEqualByComparingTo("1300.00");
        assertThat(plano.get("saldoAtual").decimalValue()).isEqualByComparingTo("1340.00");
        assertThat(plano.get("ganhoDeHoje").decimalValue()).isEqualByComparingTo("40.00");
    }

    @Test
    void aContaDeForaSaiDoSaldo() {
        HttpResponse<String> resposta = enviar("PUT", "/api/planejamento/contas", token,
            "{\"contasDeFora\":[" + especie + "]}");
        assertThat(resposta.statusCode()).isEqualTo(OK);

        JsonNode plano = obter("/api/planejamento");
        assertThat(plano.get("saldoAtual").decimalValue()).isEqualByComparingTo("1000.00");

        JsonNode contas = obter("/api/planejamento/contas");
        assertThat(entra(contas, banco)).isTrue();
        assertThat(entra(contas, especie)).isFalse();
    }

    /** Levar dinheiro do banco para a carteira o tira do planejamento, sem ser despesa. */
    @Test
    void transferirParaAContaDeForaDiminuiOSaldoDoPlanejamento() {
        enviar("PUT", "/api/planejamento/contas", token, "{\"contasDeFora\":[" + especie + "]}");
        HttpResponse<String> transferencia = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Saque\",\"valor\":200.00,\"tipo\":\"TRANSFERENCIA\",\"contaId\":" + banco
                + ",\"contaDestinoId\":" + especie + ",\"dataTransacao\":\"" + hoje.minusDays(5) + "\"}");
        assertThat(transferencia.statusCode()).isEqualTo(CRIADO);

        assertThat(obter("/api/planejamento").get("saldoAtual").decimalValue()).isEqualByComparingTo("800.00");
    }

    @Test
    void asDespesasVemPorPrazoComAFaltaAcumulada() {
        LocalDate prazoLongo = hoje.plusDays(60);
        LocalDate prazoCurto = hoje.plusDays(30);
        criarDespesa("Cartão", "800.00", prazoLongo);
        criarDespesa("Aluguel", "1500.00", prazoCurto);

        JsonNode itens = obter("/api/planejamento").get("itens");

        assertThat(itens.get(0).get("descricao").asString()).isEqualTo("Aluguel");
        assertThat(itens.get(0).get("falta").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(itens.get(0).get("diasDeTrabalho").asInt())
            .isEqualTo(CalendarioDeTrabalho.SEGUNDA_A_SABADO.contarDias(hoje, prazoCurto));
        assertThat(itens.get(1).get("acumulado").decimalValue()).isEqualByComparingTo("2300.00");
        assertThat(itens.get(1).get("falta").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(itens.get(1).get("situacao").asString()).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    void despesaDeOutroUsuarioNaoEAlcancada() {
        long despesa = criarDespesa("Aluguel", "1500.00", hoje.plusDays(30));
        String outroToken = criarUsuarioEObterToken();

        assertThat(enviar("PUT", "/api/despesas-planejadas/" + despesa, outroToken,
            "{\"descricao\":\"Minha\",\"valor\":1.00,\"prazo\":\"" + hoje + "\"}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + despesa, outroToken, null).statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(obter("/api/planejamento", outroToken).get("itens")).isEmpty();
    }

    @Test
    void naoMarcaContaDeOutroUsuario() {
        String outroToken = criarUsuarioEObterToken();
        long contaAlheia = criarConta(outroToken, "Alheia");

        HttpResponse<String> resposta = enviar("PUT", "/api/planejamento/contas", token,
            "{\"contasDeFora\":[" + contaAlheia + "]}");

        assertThat(resposta.statusCode()).isEqualTo(NAO_ENCONTRADO);
    }

    @Test
    void recusaDespesaInvalidaNoCampoQueFalhou() {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            "{\"descricao\":\"Aluguel\",\"valor\":-5}");

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        JsonNode erros = objectMapper.readTree(resposta.body()).get("erros");
        assertThat(erros.has("valor")).isTrue();
        assertThat(erros.has("prazo")).isTrue();
    }

    @Test
    void editarEExcluirADespesa() {
        long despesa = criarDespesa("Aluguel", "1500.00", hoje.plusDays(30));

        HttpResponse<String> edicao = enviar("PUT", "/api/despesas-planejadas/" + despesa, token,
            "{\"descricao\":\"Aluguel de outubro\",\"valor\":1600.00,\"prazo\":\"" + hoje.plusDays(31) + "\"}");
        assertThat(edicao.statusCode()).isEqualTo(OK);
        assertThat(obter("/api/planejamento").get("itens").get(0).get("valor").decimalValue())
            .isEqualByComparingTo("1600.00");

        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + despesa, token, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);
        assertThat(obter("/api/planejamento").get("itens")).isEmpty();
    }

    /** A marca de fora não pode impedir a exclusão da conta: o CASCADE da V11 a leva junto. */
    @Test
    void excluirAContaDeForaLevaAMarcaJunto() {
        long vazia = criarConta(token, "Vazia");
        enviar("PUT", "/api/planejamento/contas", token, "{\"contasDeFora\":[" + vazia + "]}");

        assertThat(enviar("DELETE", "/api/contas/" + vazia, token, null).statusCode()).isEqualTo(SEM_CONTEUDO);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM contas_fora_do_planejamento WHERE conta_id = ?", Long.class, vazia
        )).isZero();
    }

    private boolean entra(JsonNode contas, long contaId) {
        for (JsonNode conta : contas) {
            if (conta.get("id").asLong() == contaId) {
                return conta.get("entraNoPlanejamento").asBoolean();
            }
        }
        throw new AssertionError("conta " + contaId + " fora da lista: " + contas);
    }

    private long criarConta(String tokenUsado, String nome) {
        HttpResponse<String> resposta = enviar("POST", "/api/contas", tokenUsado,
            "{\"nome\":\"" + nome + "\",\"saldoInicial\":0}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return extrairId(resposta.body());
    }

    private long criarDespesa(String descricao, String valor, LocalDate prazo) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            "{\"descricao\":\"" + descricao + "\",\"valor\":" + valor + ",\"prazo\":\"" + prazo + "\"}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return extrairId(resposta.body());
    }

    private void receber(long contaId, String valor, LocalDate data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Ganho\",\"valor\":" + valor + ",\"tipo\":\"RECEITA\",\"categoriaId\":"
                + categoriaReceita + ",\"contaId\":" + contaId + ",\"dataTransacao\":\"" + data + "\"}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private JsonNode obter(String caminho) {
        return obter(caminho, token);
    }

    private JsonNode obter(String caminho, String tokenUsado) {
        HttpResponse<String> resposta = enviar("GET", caminho, tokenUsado, null);
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);
        return objectMapper.readTree(resposta.body());
    }

    private long extrairId(String corpo) {
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    /** Ver {@code ListagemPaginadaTest}: o token é assinado aqui para não esgotar o limite do login. */
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
