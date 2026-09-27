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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O saldo recortado por período, contra o banco de verdade.
 *
 * <p>Pelo mesmo motivo que {@link ListagemPaginadaTest} sobe Tomcat e PostgreSQL: as somas
 * passaram a ser montadas por Criteria, com predicados que só existem quando o período é
 * pedido, e o que quebra nesse tipo de consulta — tipo de parâmetro que o banco não infere,
 * {@code SUM} de nenhuma linha voltando nulo — só aparece no SQL de fato executado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaldoPorPeriodoTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int REQUISICAO_INVALIDA = 400;

    private static final String PREFIXO = "saldo-periodo-";
    private static final String SETEMBRO = "dataInicio=2026-09-01&dataFim=2026-09-30";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @LocalServerPort
    private int porta;

    private String token;
    private long contaOrigem;
    private long contaDestino;
    private long categoriaReceita;
    private long categoriaDespesa;

    /**
     * Setembro de 2026 cercado dos dois lados: um lançamento no último dia de agosto e outro
     * no primeiro de outubro, e dois dentro do mês exatamente nas pontas. Um recorte que
     * errasse a inclusão de qualquer extremo mudaria um dos totais.
     */
    @BeforeEach
    void prepararCenario() {
        token = criarContaEObterToken();

        contaOrigem = extrairId(enviar("POST", "/api/contas", token,
            "{\"nome\":\"Origem\",\"saldoInicial\":0}").body());
        contaDestino = extrairId(enviar("POST", "/api/contas", token,
            "{\"nome\":\"Destino\",\"saldoInicial\":0}").body());
        categoriaReceita = criarCategoria("Trabalho", "RECEITA");
        categoriaDespesa = criarCategoria("Mercado", "DESPESA");

        lancar("RECEITA", categoriaReceita, "1000.00", "2026-08-31");
        lancar("DESPESA", categoriaDespesa, "200.00", "2026-09-01");
        lancar("RECEITA", categoriaReceita, "500.00", "2026-09-15");
        lancar("DESPESA", categoriaDespesa, "50.00", "2026-09-30");
        lancar("DESPESA", categoriaDespesa, "70.00", "2026-10-01");
        transferir("300.00", "2026-09-10");
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void semPeriodoORetornoEOSaldoDeSempre() {
        String saldo = obter("/api/saldo");

        assertThat(valorEm(saldo, "totalReceitas")).isEqualByComparingTo("1500.00");
        assertThat(valorEm(saldo, "totalDespesas")).isEqualByComparingTo("320.00");
        assertThat(valorEm(saldo, "saldo")).isEqualByComparingTo("1180.00");
    }

    @Test
    void oMesIncluiOPrimeiroEOUltimoDiaENadaDeFora() {
        String saldo = obter("/api/saldo?" + SETEMBRO);

        assertThat(valorEm(saldo, "totalReceitas")).isEqualByComparingTo("500.00");
        assertThat(valorEm(saldo, "totalDespesas"))
            .as("200 do dia 1º e 50 do dia 30 — os 70 de 1º de outubro ficam fora")
            .isEqualByComparingTo("250.00");
        assertThat(valorEm(saldo, "saldo"))
            .as("com período, o saldo é o resultado dele, e não o acumulado até a data")
            .isEqualByComparingTo("250.00");
        assertThat(valorEm(saldo, "totalTransferencias")).isEqualByComparingTo("0");
    }

    /**
     * As pontas são independentes, como na listagem. Só com o fim, a resposta é o saldo
     * acumulado até aquele dia; só com o início, o que aconteceu dali em diante.
     */
    @Test
    void cadaPontaDoPeriodoFiltraSozinha() {
        assertThat(valorEm(obter("/api/saldo?dataFim=2026-08-31"), "saldo")).isEqualByComparingTo("1000.00");
        assertThat(valorEm(obter("/api/saldo?dataInicio=2026-10-01"), "totalDespesas")).isEqualByComparingTo("70.00");
        assertThat(valorEm(obter("/api/saldo?dataInicio=2026-10-01"), "totalReceitas")).isEqualByComparingTo("0");
    }

    /**
     * O ponto de montar a soma pela mesma especificação da listagem: o total que o painel
     * mostra para um período e a lista desse período não têm como discordar sobre quais
     * lançamentos entram.
     */
    @Test
    void oTotalDoPeriodoEASomaDaListagemDoMesmoPeriodo() {
        String despesasListadas = obter("/api/transacoes?tipo=DESPESA&tamanho=100&" + SETEMBRO);

        BigDecimal somaDaLista = Pattern.compile("\"valor\":(-?[\\d.]+)").matcher(despesasListadas).results()
            .map(achado -> new BigDecimal(achado.group(1)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(valorEm(obter("/api/saldo?" + SETEMBRO), "totalDespesas")).isEqualByComparingTo(somaDaLista);
    }

    /**
     * A transferência de 10/09 é uma linha só, com uma data só: ela entra no mês inteira, nas
     * duas contas, ou fica fora dele inteira. Por isso o consolidado do período continua sem
     * transferência, e as contas somadas continuam batendo com ele.
     */
    @Test
    void oPeriodoTambemRecortaALeituraPorConta() {
        String destino = obter("/api/saldo?contaId=" + contaDestino + "&" + SETEMBRO);
        String origem = obter("/api/saldo?contaId=" + contaOrigem + "&" + SETEMBRO);

        assertThat(valorEm(destino, "totalTransferencias")).isEqualByComparingTo("300.00");
        assertThat(valorEm(destino, "totalReceitas"))
            .as("dinheiro vindo de outra conta do mesmo dono não é receita, em período nenhum")
            .isEqualByComparingTo("0");
        assertThat(valorEm(origem, "totalTransferencias")).isEqualByComparingTo("-300.00");
        assertThat(valorEm(origem, "saldo")).isEqualByComparingTo("-50.00");
        assertThat(valorEm(origem, "saldo").add(valorEm(destino, "saldo")))
            .isEqualByComparingTo(valorEm(obter("/api/saldo?" + SETEMBRO), "saldo"));

        assertThat(valorEm(obter("/api/saldo?contaId=" + contaDestino + "&dataFim=2026-09-09"), "saldo"))
            .as("na véspera da transferência, a conta de destino ainda não tinha nada")
            .isEqualByComparingTo("0");
    }

    @Test
    void periodoSemLancamentoDevolveZeroENaoNulo() {
        String saldo = obter("/api/saldo?dataInicio=2027-01-01&dataFim=2027-01-31");

        assertThat(saldo).doesNotContain("null");
        assertThat(valorEm(saldo, "saldo")).isEqualByComparingTo("0");
    }

    /**
     * Recorte invertido é recorte vazio, não erro — a listagem trata assim, e as duas rotas
     * precisam concordar sobre o mesmo pedido.
     */
    @Test
    void recorteInvertidoEVazioComoNaListagem() {
        assertThat(valorEm(obter("/api/saldo?dataInicio=2026-09-30&dataFim=2026-09-01"), "totalDespesas"))
            .isEqualByComparingTo("0");
    }

    @Test
    void dataMalformadaERecusada() {
        assertThat(enviar("GET", "/api/saldo?dataInicio=setembro", token, null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
    }

    @Test
    void umUsuarioNaoEnxergaOPeriodoDeOutro() {
        String outro = criarContaEObterToken();
        HttpResponse<String> resposta = enviar("GET", "/api/saldo?" + SETEMBRO, outro, null);

        assertThat(resposta.statusCode()).isEqualTo(OK);
        assertThat(valorEm(resposta.body(), "totalDespesas"))
            .as("o id do dono sai do SecurityContext, nunca de parâmetro da requisição")
            .isEqualByComparingTo("0");
    }

    private String obter(String caminho) {
        HttpResponse<String> resposta = enviar("GET", caminho, token, null);
        assertThat(resposta.statusCode()).as("GET %s: %s", caminho, resposta.body()).isEqualTo(OK);
        return resposta.body();
    }

    private void lancar(String tipo, long categoriaId, String valor, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"" + tipo + " " + data + "\",\"valor\":" + valor + ",\"tipo\":\"" + tipo
                + "\",\"categoriaId\":" + categoriaId + ",\"contaId\":" + contaOrigem
                + ",\"dataTransacao\":\"" + data + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private void transferir(String valor, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Transferida\",\"valor\":" + valor + ",\"tipo\":\"TRANSFERENCIA\",\"contaId\":"
                + contaOrigem + ",\"contaDestinoId\":" + contaDestino
                + ",\"dataTransacao\":\"" + data + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private long criarCategoria(String nome, String tipo) {
        HttpResponse<String> resposta = enviar("POST", "/api/categorias", token,
            "{\"nome\":\"" + nome + "\",\"tipo\":\"" + tipo + "\"}");

        return extrairId(resposta.body());
    }

    private long extrairId(String corpo) {
        Matcher achado = Pattern.compile("\"id\":(\\d+)").matcher(corpo);
        assertThat(achado.find()).as("resposta sem id: %s", corpo).isTrue();
        return Long.parseLong(achado.group(1));
    }

    private BigDecimal valorEm(String corpo, String campo) {
        Matcher achado = Pattern.compile("\"" + campo + "\":(-?[\\d.]+)").matcher(corpo);
        assertThat(achado.find()).as("resposta sem %s: %s", campo, corpo).isTrue();
        return new BigDecimal(achado.group(1));
    }

    /** Ver {@link ListagemPaginadaTest}: o token é assinado aqui para não esgotar o limite do login. */
    private String criarContaEObterToken() {
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
