package com.gefinx.backend.compartilhado.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que a origem permitida vem de <b>configuração</b>, e não do código.
 *
 * <p>{@link CorsTest} cobre a política com o valor padrão, e por isso não distingue uma
 * origem lida da configuração de uma escrita literalmente na classe: os dois casos
 * produzem a mesma resposta enquanto o padrão for o endereço do Vite. Aqui a origem
 * autorizada é outra, declarada só para esta classe — se alguém voltar a fixar
 * {@code localhost:5173} em {@code CorsConfig}, o primeiro teste falha.
 *
 * <p>Isso importa porque um endereço de {@code localhost} compilado dentro do artefato não
 * tem como estar certo em produção, e a falha apareceria só depois de publicar.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "gefinx.cors.origens=https://gefinx.exemplo"
)
class CorsConfiguravelTest {

    private static final String ORIGEM_CONFIGURADA = "https://gefinx.exemplo";
    private static final String ORIGEM_PADRAO = "http://localhost:5173";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @LocalServerPort
    private int porta;

    @Test
    void aOrigemDeclaradaNaConfiguracaoEAceita() {
        var resposta = preflight(ORIGEM_CONFIGURADA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin"))
            .as("a origem veio da configuração desta classe, e não de um literal no código")
            .hasValue(ORIGEM_CONFIGURADA);
    }

    @Test
    void aOrigemDeDesenvolvimentoDeixaDeSerAceitaQuandoNaoEstaConfigurada() {
        var resposta = preflight(ORIGEM_PADRAO);

        assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin"))
            .as("o endereço do Vite só é permitido por ser o padrão; declarada outra origem, ele sai")
            .isEmpty();
    }

    private HttpResponse<String> preflight(String origem) {
        HttpRequest requisicao = HttpRequest.newBuilder(
                URI.create("http://localhost:" + porta + "/api/auth/login"))
            .header("Origin", origem)
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build();

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
