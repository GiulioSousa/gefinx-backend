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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A listagem paginada e filtrada, contra o banco de verdade.
 *
 * <p>Sobe Tomcat e PostgreSQL de propósito, e não é preciosismo: a primeira versão desta
 * consulta usava {@code :x IS NULL OR campo = :x} em JPQL, compilava, subia o contexto e
 * passaria em qualquer teste com repositório dublado — e estourava `500` no PostgreSQL assim
 * que alguém filtrava por data, porque um parâmetro à esquerda de {@code IS NULL} não tem de
 * onde tirar o tipo. Só o SQL de fato executado revela isso, então é o SQL de fato executado
 * que este teste exercita.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ListagemPaginadaTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int REQUISICAO_INVALIDA = 400;

    private static final String PREFIXO = "paginacao-";

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
    private long categoriaDespesa;

    /**
     * Cada execução monta o próprio usuário, com as próprias contas e lançamentos.
     * Reaproveitar dados do banco de desenvolvimento faria o resultado depender do que o dono
     * do projeto lançou na véspera.
     */
    @BeforeEach
    void prepararCenario() {
        token = criarContaEObterToken();

        contaOrigem = extrairId(enviar("POST", "/api/contas", token,
            "{\"nome\":\"Origem\",\"saldoInicial\":0}").body());
        contaDestino = extrairId(enviar("POST", "/api/contas", token,
            "{\"nome\":\"Destino\",\"saldoInicial\":0}").body());
        categoriaDespesa = criarCategoria("Mercado", "DESPESA");

        lancarDespesa("Mais antiga", "2026-01-10");
        lancarDespesa("Do meio", "2026-02-20");
        // Mesma data da anterior, de propósito: é o único par que exercita o desempate por
        // id. Com todas as datas distintas, a ordenação daria o mesmo resultado com ou sem
        // desempate, e os testes de ordem passariam sem provar nada sobre ele.
        lancarDespesa("Do mesmo dia", "2026-02-20");
        lancarDespesa("Mais recente", "2026-03-30");
        lancarTransferencia("Transferida", "2026-02-25");
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void listaVemDaMaisRecenteParaAMaisAntiga() {
        assertThat(descricoesDe("/api/transacoes"))
            .containsExactly("Mais recente", "Transferida", "Do mesmo dia", "Do meio", "Mais antiga");
    }

    @Test
    void aPaginaTemOTamanhoPedidoEOsMetadadosDescrevemOTodo() {
        String primeira = enviar("GET", "/api/transacoes?pagina=0&tamanho=2", token, null).body();

        assertThat(descricoesDe("/api/transacoes?pagina=0&tamanho=2"))
            .containsExactly("Mais recente", "Transferida");
        assertThat(numeroEm(primeira, "totalItens"))
            .as("o total descreve o histórico inteiro, não a fatia devolvida")
            .isEqualTo(5);
        assertThat(numeroEm(primeira, "totalPaginas")).isEqualTo(3);
    }

    @Test
    void aSegundaPaginaContinuaDeOndeAPrimeiraParouSemRepetir() {
        assertThat(descricoesDe("/api/transacoes?pagina=1&tamanho=2"))
            .as("sem desempate estável, uma linha apareceria nas duas páginas ou em nenhuma")
            .containsExactly("Do mesmo dia", "Do meio");
    }

    @Test
    void paginaAlemDoFimVemVaziaSemErro() {
        HttpResponse<String> resposta = enviar("GET", "/api/transacoes?pagina=99&tamanho=2", token, null);

        assertThat(resposta.statusCode()).isEqualTo(OK);
        assertThat(descricoesDe("/api/transacoes?pagina=99&tamanho=2")).isEmpty();
        assertThat(numeroEm(resposta.body(), "totalItens")).isEqualTo(5);
    }

    /**
     * O caso que o JPQL anterior derrubava com `500`. Se ele voltar, é aqui que aparece.
     */
    @Test
    void filtroPorPeriodoIncluiOsDoisExtremos() {
        assertThat(descricoesDe("/api/transacoes?dataInicio=2026-01-10&dataFim=2026-02-20"))
            .as("as datas são inclusivas: quem pede 10/01 a 20/02 espera os dois dias dentro")
            .containsExactly("Do mesmo dia", "Do meio", "Mais antiga");
    }

    @Test
    void filtroPorTipoSeparaTransferenciaDeDespesa() {
        assertThat(descricoesDe("/api/transacoes?tipo=TRANSFERENCIA")).containsExactly("Transferida");
        assertThat(descricoesDe("/api/transacoes?tipo=DESPESA"))
            .containsExactly("Mais recente", "Do mesmo dia", "Do meio", "Mais antiga");
    }

    /**
     * O ponto de domínio do filtro por conta. Uma transferência é uma linha só que afeta duas
     * contas, e pertence ao extrato das duas — o saldo por conta já a considera nos dois lados
     * desde a Etapa 20. Se o filtro olhasse apenas {@code conta_id}, o extrato da conta de
     * destino omitiria o dinheiro que ela recebeu, e extrato e saldo passariam a discordar
     * sobre a mesma conta.
     */
    @Test
    void filtroPorContaEnxergaAsDuasPontasDaTransferencia() {
        assertThat(descricoesDe("/api/transacoes?contaId=" + contaDestino))
            .as("a conta de destino não é origem de nada, e ainda assim recebeu a transferência")
            .containsExactly("Transferida");
        assertThat(descricoesDe("/api/transacoes?contaId=" + contaOrigem))
            .contains("Transferida");
    }

    @Test
    void filtrosSeCombinam() {
        assertThat(descricoesDe("/api/transacoes?tipo=DESPESA&dataInicio=2026-02-01"))
            .containsExactly("Mais recente", "Do mesmo dia", "Do meio");
    }

    @Test
    void filtroPorContaDeOutroDonoVemVazioEmVezDeConfirmarQueElaExiste() {
        assertThat(descricoesDe("/api/transacoes?contaId=" + (contaDestino + 100_000)))
            .as("a consulta é fechada por usuário antes de qualquer filtro, então não há o que vazar")
            .isEmpty();
    }

    @Test
    void tamanhoAlemDoTetoERecusadoNoCampoQueFalhou() {
        HttpResponse<String> resposta = enviar("GET", "/api/transacoes?tamanho=101", token, null);

        assertThat(resposta.statusCode())
            .as("sem teto, ?tamanho=1000000 desfaria em silêncio o que a paginação garante")
            .isEqualTo(REQUISICAO_INVALIDA);
        assertThat(resposta.body())
            .as("o erro precisa cair no campo, e não só na mensagem geral — é a garantia da Etapa 16")
            .contains("\"tamanho\":");
    }

    @Test
    void tamanhoZeroEPaginaNegativaSaoRecusados() {
        assertThat(enviar("GET", "/api/transacoes?tamanho=0", token, null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
        assertThat(enviar("GET", "/api/transacoes?pagina=-1", token, null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
    }

    @Test
    void umUsuarioNaoEnxergaOHistoricoDeOutro() {
        String outro = criarContaEObterToken();

        assertThat(descricoesDe("/api/transacoes", outro))
            .as("o id do dono sai do SecurityContext, nunca de parâmetro da requisição")
            .isEmpty();
    }

    private List<String> descricoesDe(String caminho) {
        return descricoesDe(caminho, token);
    }

    private List<String> descricoesDe(String caminho, String tokenUsado) {
        HttpResponse<String> resposta = enviar("GET", caminho, tokenUsado, null);
        assertThat(resposta.statusCode()).isEqualTo(OK);

        Matcher achado = Pattern.compile("\"descricao\":\"([^\"]+)\"").matcher(resposta.body());
        return achado.results().map(resultado -> resultado.group(1)).toList();
    }

    private void lancarDespesa(String descricao, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"" + descricao + "\",\"valor\":10.00,\"tipo\":\"DESPESA\",\"categoriaId\":"
                + categoriaDespesa + ",\"contaId\":" + contaOrigem
                + ",\"dataTransacao\":\"" + data + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private void lancarTransferencia(String descricao, String data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"" + descricao + "\",\"valor\":10.00,\"tipo\":\"TRANSFERENCIA\",\"contaId\":"
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
        return numeroEm(corpo, "id");
    }

    private long numeroEm(String corpo, String campo) {
        Matcher achado = Pattern.compile("\"" + campo + "\":(\\d+)").matcher(corpo);
        assertThat(achado.find()).as("resposta sem %s: %s", campo, corpo).isTrue();
        return Long.parseLong(achado.group(1));
    }

    /**
     * O token é assinado aqui, sem passar por {@code POST /api/auth/login}.
     *
     * <p>Pela porta da frente, esta classe não passa: o login é limitado a 5 por minuto por
     * origem desde a Etapa 4, e uma classe com uma dúzia de testes esgota o balde no meio.
     * Pior, o sintoma engana — o login barrado devolve `429`, o token sai vazio e a falha
     * aparece como `401` na listagem, longe da causa. O limite está certo e é testado onde
     * deve, em {@code LimiteDeRequisicoesTest}; aqui ele só atrapalha o cenário, porque o
     * que está sob teste é a listagem.
     */
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
