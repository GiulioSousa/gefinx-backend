package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a etapa de ponta a ponta: um token que funcionava para de funcionar depois que o
 * dono encerra as sessões. Sobe Tomcat de verdade porque o que está sob teste é a cadeia de
 * filtros inteira, não uma classe isolada.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // O limite por origem não é o objeto deste teste. Sem afrouxá-lo, o balde compartilhado
    // pelos logins desta classe faria o resultado depender da ordem de execução, e a falha
    // apareceria como 401 na consulta de saldo — longe da causa.
    properties = "gefinx.limite-requisicoes.login.tentativas=100"
)
class RevogacaoDeSessaoTest {

    private static final int OK = 200;
    private static final int SEM_CONTEUDO = 204;
    private static final int NAO_AUTORIZADO = 401;

    private static final String PREFIXO = "revogacao-";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * O teste precisa de contas de verdade — sem elas não há token para revogar — e não há
     * endpoint que as apague. Sem esta limpeza, cada execução deixaria usuários órfãos no
     * banco de desenvolvimento.
     */
    @AfterEach
    void removerContasDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @LocalServerPort
    private int porta;

    @Test
    void encerrarSessoesInvalidaOTokenQueEstavaEmUso() {
        String token = criarContaEEntrar(nomeDeUsuario());

        assertThat(consultarSaldo(token)).isEqualTo(OK);
        assertThat(encerrarSessoes(token)).isEqualTo(SEM_CONTEUDO);
        assertThat(consultarSaldo(token))
            .as("o token continuava assinado e no prazo; o que mudou foi a marca de revogação")
            .isEqualTo(NAO_AUTORIZADO);
    }

    @Test
    void umLoginNovoVoltaAFuncionarDepoisDeEncerrar() {
        String usuario = nomeDeUsuario();
        String token = criarContaEEntrar(usuario);
        encerrarSessoes(token);

        assertThat(consultarSaldo(autenticar(usuario))).isEqualTo(OK);
    }

    @Test
    void encerrarSessoesExigeAutenticacao() {
        assertThat(enviar("DELETE", "/api/sessoes", null, null).statusCode())
            .as("sob /api/auth/** o endpoint nasceria público, e qualquer um derrubaria sessões alheias")
            .isEqualTo(NAO_AUTORIZADO);
    }

    private String nomeDeUsuario() {
        return PREFIXO + System.nanoTime();
    }

    /**
     * A conta nasce por INSERT, como nasce em produção: não há mais rota de cadastro. O
     * token, esse continua vindo do login de verdade — é a cadeia inteira que está sob teste.
     */
    private String criarContaEEntrar(String usuario) {
        ContasDeTeste.criar(jdbcTemplate, usuario);
        return autenticar(usuario);
    }

    private String autenticar(String usuario) {
        var resposta = enviar("POST", "/api/auth/login", null,
            "{\"usuario\":\"" + usuario + "\",\"senha\":\"" + ContasDeTeste.SENHA + "\"}");
        return extrairToken(resposta.body());
    }

    private int consultarSaldo(String token) {
        return enviar("GET", "/api/saldo", token, null).statusCode();
    }

    private int encerrarSessoes(String token) {
        return enviar("DELETE", "/api/sessoes", token, null).statusCode();
    }

    private String extrairToken(String corpo) {
        return corpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String token, String corpo) {
        HttpRequest.BodyPublisher publisher = corpo == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(corpo);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .method(metodo, publisher);

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
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
