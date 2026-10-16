package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A troca de senha de ponta a ponta, com Tomcat de verdade: o que está sob teste é a cadeia de
 * filtros, a revogação das sessões e o token novo, e não uma classe isolada.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // Ver RevogacaoDeSessaoTest: o limite por origem não é o objeto deste teste.
    properties = "gefinx.limite-requisicoes.login.tentativas=100"
)
class TrocaDeSenhaTest {

    private static final int OK = 200;
    private static final int REQUISICAO_INVALIDA = 400;
    private static final int NAO_AUTORIZADO = 401;

    private static final String PREFIXO = "troca-de-senha-";
    private static final String NOVA_SENHA = "outra frase de senha";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int porta;

    @AfterEach
    void removerContasDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    /**
     * O token antigo para de valer — a troca encerra todas as sessões —, mas este aparelho
     * continua com o token novo da resposta. A senha nova entra; a antiga, não.
     */
    @Test
    void trocaASenhaEContinuaNaSessaoComOTokenNovo() {
        String usuario = nomeDeUsuario();
        ContasDeTeste.criar(jdbcTemplate, usuario);
        String tokenAntigo = entrar(usuario, ContasDeTeste.SENHA).get("token").asString();

        HttpResponse<String> resposta = trocar(tokenAntigo, ContasDeTeste.SENHA, NOVA_SENHA);

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);
        String tokenNovo = objectMapper.readTree(resposta.body()).get("token").asString();
        assertThat(consultarSaldo(tokenAntigo)).isEqualTo(NAO_AUTORIZADO);
        assertThat(consultarSaldo(tokenNovo)).isEqualTo(OK);
        assertThat(enviar("POST", "/api/auth/login", null, login(usuario, ContasDeTeste.SENHA)).statusCode())
            .isEqualTo(NAO_AUTORIZADO);
        assertThat(entrar(usuario, NOVA_SENHA).get("usuario").asString()).isEqualTo(usuario);
    }

    @Test
    void recusaASenhaAtualErradaNoCampoDelaSemTrocar() {
        String usuario = nomeDeUsuario();
        ContasDeTeste.criar(jdbcTemplate, usuario);
        String token = entrar(usuario, ContasDeTeste.SENHA).get("token").asString();

        HttpResponse<String> resposta = trocar(token, "nao e a senha", NOVA_SENHA);

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(resposta.body()).get("erros").get("senhaAtual").asString())
            .isEqualTo("A senha atual não confere");
        assertThat(consultarSaldo(token)).as("nada mudou, nem as sessões").isEqualTo(OK);
    }

    @Test
    void recusaANovaSenhaPrevisivelNoCampoDela() {
        String usuario = nomeDeUsuario();
        ContasDeTeste.criar(jdbcTemplate, usuario);
        String token = entrar(usuario, ContasDeTeste.SENHA).get("token").asString();

        HttpResponse<String> resposta = trocar(token, ContasDeTeste.SENHA, "1234567890");

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(resposta.body()).get("erros").has("novaSenha")).isTrue();
    }

    @Test
    void exigeAutenticacao() {
        assertThat(trocar(null, ContasDeTeste.SENHA, NOVA_SENHA).statusCode()).isEqualTo(NAO_AUTORIZADO);
    }

    private String nomeDeUsuario() {
        return PREFIXO + System.nanoTime();
    }

    private JsonNode entrar(String usuario, String senha) {
        HttpResponse<String> resposta = enviar("POST", "/api/auth/login", null, login(usuario, senha));
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);
        return objectMapper.readTree(resposta.body());
    }

    private String login(String usuario, String senha) {
        return "{\"usuario\":\"" + usuario + "\",\"senha\":\"" + senha + "\"}";
    }

    private HttpResponse<String> trocar(String token, String senhaAtual, String novaSenha) {
        return enviar("PUT", "/api/conta/senha", token,
            "{\"senhaAtual\":\"" + senhaAtual + "\",\"novaSenha\":\"" + novaSenha + "\"}");
    }

    private int consultarSaldo(String token) {
        return enviar("GET", "/api/saldo", token, null).statusCode();
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
