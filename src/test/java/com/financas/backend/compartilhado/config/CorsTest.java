package com.financas.backend.compartilhado.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixa a política de CORS: uma origem, e sem credenciais.
 *
 * <p>O que está sob teste é a resposta que o navegador de fato recebe, e não o conteúdo do
 * bean de configuração. Um teste que lesse o {@code CorsConfiguration} confirmaria apenas
 * que o campo foi preenchido como se pretendia — o que não é a mesma coisa que a bandeira
 * chegar, ou não chegar, ao outro lado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsTest {

    private static final String ORIGEM_PERMITIDA = "http://localhost:5173";
    private static final String ORIGEM_ESTRANHA = "http://sitio-malicioso.exemplo";
    private static final String CABECALHO_CREDENCIAIS = "Access-Control-Allow-Credentials";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void aceitaAPreflightDaOrigemDoFrontend() {
        var resposta = preflight(ORIGEM_PERMITIDA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin"))
            .hasValue(ORIGEM_PERMITIDA);
    }

    @Test
    void naoAnunciaPermissaoDeCredenciaisNaPreflight() {
        var resposta = preflight(ORIGEM_PERMITIDA);

        // A autenticação viaja no cabeçalho Authorization, que o cliente monta. Anunciar
        // permissão de credenciais liberaria o navegador a anexar cookies entre origens —
        // permissão que nada aqui usa.
        assertThat(resposta.headers().firstValue(CABECALHO_CREDENCIAIS)).isEmpty();
    }

    @Test
    void naoAnunciaPermissaoDeCredenciaisNaRequisicaoReal() {
        var resposta = enviar(
            HttpRequest.newBuilder(URI.create(base() + "/api/auth/login"))
                .header("Origin", ORIGEM_PERMITIDA)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"ninguem@exemplo.com\",\"senha\":\"senha-qualquer\"}"))
                .build()
        );

        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).hasValue(ORIGEM_PERMITIDA);
        assertThat(resposta.headers().firstValue(CABECALHO_CREDENCIAIS)).isEmpty();
    }

    @Test
    void recusaAPreflightDeOutraOrigem() {
        var resposta = preflight(ORIGEM_ESTRANHA);

        assertThat(resposta.statusCode()).isNotEqualTo(200);
        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpResponse<String> preflight(String origem) {
        return enviar(
            HttpRequest.newBuilder(URI.create(base() + "/api/auth/login"))
                .header("Origin", origem)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build()
        );
    }

    private String base() {
        return "http://localhost:" + porta;
    }

    private HttpResponse<String> enviar(HttpRequest requisicao) {
        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar a API", excecao);
        }
    }
}
