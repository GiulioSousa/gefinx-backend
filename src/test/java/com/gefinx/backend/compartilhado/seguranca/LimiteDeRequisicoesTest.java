package com.gefinx.backend.compartilhado.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o limite de tentativas não pode ser evitado reescrevendo o caminho
 * da requisição.
 *
 * <p>Sobe um Tomcat de verdade de propósito: o desvio que estes testes cobrem nasce
 * da diferença entre o caminho cru que chega ao contêiner e o caminho decodificado
 * pelo qual o Spring MVC roteia. Um teste que montasse a requisição em memória
 * escolheria uma das duas formas e nunca veria essa divergência.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "gefinx.limite-requisicoes.login.tentativas=3",
        "gefinx.limite-requisicoes.login.janela=5m",
        "gefinx.limite-requisicoes.registro.tentativas=3",
        "gefinx.limite-requisicoes.registro.janela=5m"
    }
)
class LimiteDeRequisicoesTest {

    private static final int NAO_AUTORIZADO = 401;
    private static final int DADOS_INVALIDOS = 400;
    private static final int LIMITE_EXCEDIDO = 429;

    /** {@code %6C} é a letra {@code l}: o Spring MVC roteia isto para o login. */
    private static final String LOGIN_CODIFICADO = "/api/auth/%6Cogin";
    private static final String LOGIN = "/api/auth/login";

    /** {@code %72} é a letra {@code r}. */
    private static final String REGISTRO_CODIFICADO = "/api/auth/%72egistrar";
    private static final String REGISTRO = "/api/auth/registrar";

    private static final String CREDENCIAIS_INEXISTENTES =
        "{\"email\":\"ninguem@exemplo.com\",\"senha\":\"senha-qualquer\"}";
    private static final String REGISTRO_INVALIDO =
        "{\"nome\":\"\",\"email\":\"invalido\",\"senha\":\"1\"}";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void contaAsTentativasDoCaminhoCodificadoNoMesmoLimiteDoCaminhoNormal() {
        // Confirma antes que o caminho codificado realmente alcança o login: se não
        // fosse uma rota equivalente, o resto do teste passaria por engano.
        assertThat(postar(LOGIN_CODIFICADO, CREDENCIAIS_INEXISTENTES))
            .as("o caminho codificado precisa alcançar o login para que o teste tenha valor")
            .isEqualTo(NAO_AUTORIZADO);

        assertThat(postar(LOGIN, CREDENCIAIS_INEXISTENTES)).isEqualTo(NAO_AUTORIZADO);
        assertThat(postar(LOGIN, CREDENCIAIS_INEXISTENTES)).isEqualTo(NAO_AUTORIZADO);

        // As três tentativas acima esgotaram um único balde, ainda que escritas de
        // duas formas diferentes.
        assertThat(postar(LOGIN_CODIFICADO, CREDENCIAIS_INEXISTENTES))
            .as("o caminho codificado não pode escapar do limite já esgotado")
            .isEqualTo(LIMITE_EXCEDIDO);
    }

    @Test
    void aplicaOMesmoLimiteAoCaminhoCodificadoDoRegistro() {
        assertThat(postar(REGISTRO_CODIFICADO, REGISTRO_INVALIDO))
            .as("o caminho codificado precisa alcançar o registro para que o teste tenha valor")
            .isEqualTo(DADOS_INVALIDOS);

        assertThat(postar(REGISTRO, REGISTRO_INVALIDO)).isEqualTo(DADOS_INVALIDOS);
        assertThat(postar(REGISTRO, REGISTRO_INVALIDO)).isEqualTo(DADOS_INVALIDOS);

        assertThat(postar(REGISTRO_CODIFICADO, REGISTRO_INVALIDO))
            .as("o caminho codificado não pode escapar do limite já esgotado")
            .isEqualTo(LIMITE_EXCEDIDO);
    }

    /**
     * Envia o caminho exatamente como escrito. A {@link URI} é montada sem template
     * para que o cliente não normalize nem recodifique justamente o que está sob teste.
     */
    private int postar(String caminho, String corpoJson) {
        HttpRequest requisicao = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corpoJson))
            .build();

        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar " + caminho, excecao);
        }
    }
}
