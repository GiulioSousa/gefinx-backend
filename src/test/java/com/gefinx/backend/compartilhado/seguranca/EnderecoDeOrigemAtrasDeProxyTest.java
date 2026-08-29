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
 * O limite por origem continua sendo por <b>cliente</b> quando a aplicação está atrás de um
 * proxy reverso.
 *
 * <p>O filtro deriva o balde de {@code getRemoteAddr()}. Publicada atrás de um proxy, esse
 * método devolve o endereço do próprio proxy para toda requisição, e os baldes de todos os
 * usuários colapsam num só: o primeiro que errasse cinco senhas trancaria o login de todo
 * mundo. A proteção da Etapa 4 não apenas deixaria de proteger — passaria a ser o ataque.
 *
 * <p>{@code server.forward-headers-strategy=FRAMEWORK} faz o contêiner resolver o endereço
 * real a partir de {@code X-Forwarded-For} antes de a requisição chegar ao filtro. Este teste
 * fixa esse comportamento; a configuração é declarada aqui, e não no {@code application.yml},
 * porque o padrão precisa continuar sendo {@code NONE}.
 *
 * <p><b>A configuração só é segura junto de duas condições que vivem fora do código</b>, e
 * que estão registradas no {@code application.yml} e no README: o proxy tem de sobrescrever
 * {@code X-Forwarded-For}, e a aplicação tem de escutar apenas em loopback. Sem as duas, o
 * cliente forja o próprio endereço — que é exatamente o que este teste faz de propósito, para
 * provar que o cabeçalho passa a ser obedecido.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.forward-headers-strategy=FRAMEWORK"
)
class EnderecoDeOrigemAtrasDeProxyTest {

    private static final int NAO_AUTORIZADO = 401;
    private static final int LIMITE_EXCEDIDO = 429;

    /** O limite de login é 5 por minuto; a sexta tentativa do mesmo cliente é barrada. */
    private static final int ALEM_DO_LIMITE = 6;

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void clientesDiferentesAtrasDoMesmoProxyNaoCompartilhamOBalde() {
        for (int tentativa = 1; tentativa < ALEM_DO_LIMITE; tentativa++) {
            assertThat(tentarLogin("1.2.3.4")).isEqualTo(NAO_AUTORIZADO);
        }

        assertThat(tentarLogin("1.2.3.4"))
            .as("o cliente que gastou as cinco tentativas é barrado")
            .isEqualTo(LIMITE_EXCEDIDO);

        assertThat(tentarLogin("9.9.9.9"))
            .as("outro cliente chega com o balde intacto — sem isso, um usuário tranca o login de todos")
            .isEqualTo(NAO_AUTORIZADO);

        assertThat(tentarLogin("1.2.3.4"))
            .as("e o bloqueio do primeiro não foi afetado pelo segundo")
            .isEqualTo(LIMITE_EXCEDIDO);
    }

    private int tentarLogin(String enderecoDoCliente) {
        HttpRequest requisicao = HttpRequest.newBuilder(
                URI.create("http://localhost:" + porta + "/api/auth/login"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", enderecoDoCliente)
            .POST(HttpRequest.BodyPublishers.ofString(
                "{\"email\":\"proxy-" + enderecoDoCliente + "@exemplo.com\",\"senha\":\"uma senha qualquer\"}"))
            .build();

        try {
            HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
            return resposta.statusCode();
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar a API", excecao);
        }
    }
}
