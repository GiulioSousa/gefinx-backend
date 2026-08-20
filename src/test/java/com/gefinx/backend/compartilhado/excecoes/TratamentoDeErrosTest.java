package com.gefinx.backend.compartilhado.excecoes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que erro de cliente chega ao cliente como erro de cliente.
 *
 * <p>Sobe a aplicação inteira de propósito: os casos aqui nascem antes do controlador
 * — na rota que não existe, no corpo que o conversor não consegue ler, no método que a
 * rota não aceita. Um teste que chamasse o manipulador diretamente passaria por engano,
 * porque a exceção que ele receberia seria a que o teste escolheu, e não a que a pilha
 * de fato levanta.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // O limite por origem não é o objeto deste teste, e barraria as requisições
        // antes que elas chegassem ao ponto que interessa.
        "financas.limite-requisicoes.login.tentativas=100",
        "financas.limite-requisicoes.registro.tentativas=100"
    }
)
class TratamentoDeErrosTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String CREDENCIAIS = "{\"email\":\"ninguem@exemplo.com\",\"senha\":\"senha-qualquer\"}";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** As rotas protegidas exigem conta de verdade, e não há endpoint que as apague. */
    @AfterEach
    void removerContasDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE email LIKE 'erros-%'");
    }

    @Test
    void devolveNaoEncontradoParaRotaInexistente() {
        var resposta = enviar("POST", "/api/auth/rota-que-nao-existe", CREDENCIAIS.getBytes(StandardCharsets.UTF_8), "application/json");

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertThat(resposta.body()).contains("\"status\":404");
    }

    @Test
    void devolveNaoEncontradoParaRotaCorretaComBarraNoFim() {
        // A barra final deixou de casar a rota no Spring 6; sem manipulador próprio,
        // isso aparecia como falha do servidor.
        var resposta = enviar("POST", LOGIN + "/", CREDENCIAIS.getBytes(StandardCharsets.UTF_8), "application/json");

        assertThat(resposta.statusCode()).isEqualTo(404);
    }

    @Test
    void devolveDadosInvalidosParaJsonTruncado() {
        var resposta = enviar("POST", LOGIN, "{\"email\":".getBytes(StandardCharsets.UTF_8), "application/json");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("\"status\":400");
    }

    @Test
    void devolveDadosInvalidosParaCampoComTipoErrado() {
        var corpo = "{\"email\":{\"interno\":1},\"senha\":\"senha-qualquer\"}";

        var resposta = enviar("POST", LOGIN, corpo.getBytes(StandardCharsets.UTF_8), "application/json");

        assertThat(resposta.statusCode()).isEqualTo(400);
    }

    @Test
    void devolveDadosInvalidosParaBytesInvalidosEmUtf8() {
        byte[] corpo = new byte[]{'{', '"', 'e', 'm', 'a', 'i', 'l', '"', ':', '"', (byte) 0xFF, (byte) 0xFE, '"', '}'};

        var resposta = enviar("POST", LOGIN, corpo, "application/json");

        assertThat(resposta.statusCode()).isEqualTo(400);
    }

    @Test
    void devolveMetodoNaoPermitidoQuandoARotaNaoAceitaOVerbo() {
        var resposta = enviar("GET", LOGIN, null, null);

        assertThat(resposta.statusCode()).isEqualTo(405);
        assertThat(resposta.headers().firstValue("Allow")).isPresent();
    }

    @Test
    void devolveTipoNaoSuportadoParaCorpoQueNaoEJson() {
        var resposta = enviar("POST", LOGIN, "email=alguem".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(resposta.statusCode()).isEqualTo(415);
    }

    @Test
    void mantemOFormatoUniformeDeErroEmTodosOsCasos() {
        var resposta = enviar("POST", LOGIN, "{".getBytes(StandardCharsets.UTF_8), "application/json");

        assertThat(resposta.headers().firstValue("Content-Type")).hasValueSatisfying(
            tipo -> assertThat(tipo).contains("application/json")
        );
        assertThat(resposta.body())
            .contains("\"momento\"")
            .contains("\"status\"")
            .contains("\"mensagem\"")
            .contains("\"erros\"");
    }

    @Test
    void devolveOFormatoUniformeTambemNoNaoAutenticado() {
        var resposta = enviar("GET", "/api/saldo", null, null);

        assertThat(resposta.statusCode()).isEqualTo(401);
        // Antes esta resposta saía com Content-Length: 0 — a única da API fora do formato,
        // e justamente a que o frontend lê para decidir encerrar a sessão.
        assertThat(resposta.body()).isNotEmpty();
        assertThat(resposta.body())
            .contains("\"status\":401")
            .contains("\"mensagem\":\"Não autenticado\"")
            .contains("\"momento\"")
            .contains("\"erros\"");
        assertThat(resposta.headers().firstValue("Content-Type")).hasValueSatisfying(
            tipo -> assertThat(tipo).contains("application/json")
        );
    }

    @Test
    void naoRevelaSeOTokenEstaAusenteOuInvalido() {
        var semToken = enviar("GET", "/api/saldo", null, null);
        var comTokenInvalido = enviarComToken("GET", "/api/saldo", "token.claramente.invalido");

        assertThat(comTokenInvalido.statusCode()).isEqualTo(401);
        assertThat(semOMomento(comTokenInvalido.body()))
            .as("a diferença não muda o que o cliente faz, e enunciá-la conta a quem sonda em que estado está o token")
            .isEqualTo(semOMomento(semToken.body()));
    }

    /** O instante é o único campo que muda entre duas respostas de erro iguais. */
    private String semOMomento(String corpo) {
        return corpo.replaceAll("\"momento\":\"[^\"]+\",", "");
    }

    @Test
    void devolveDadosInvalidosQuandoOIdNoCaminhoNaoENumero() {
        String token = registrarEObterToken();

        var resposta = enviarComToken("DELETE", "/api/transacoes/abc", token);

        // O id chega pelo caminho e não converte para Long. Sem manipulador próprio, errar
        // um link viraria falha do servidor.
        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("\"status\":400");
    }

    private String registrarEObterToken() {
        String email = "erros-" + System.nanoTime() + "@exemplo.com";
        String corpo = "{\"nome\":\"Fulano\",\"email\":\"" + email + "\",\"senha\":\"uma frase de senha\"}";

        var resposta = enviar("POST", "/api/auth/registrar", corpo.getBytes(StandardCharsets.UTF_8), "application/json");
        return resposta.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private HttpResponse<String> enviarComToken(String metodo, String caminho, String token) {
        try {
            var requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Authorization", "Bearer " + token)
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private HttpResponse<String> enviar(String metodo, String caminho, byte[] corpo, String tipoDeConteudo) {
        try {
            var requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
            if (tipoDeConteudo != null) {
                requisicao.header("Content-Type", tipoDeConteudo);
            }
            requisicao.method(
                metodo,
                corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(corpo)
            );
            return clienteHttp.send(requisicao.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }
}
