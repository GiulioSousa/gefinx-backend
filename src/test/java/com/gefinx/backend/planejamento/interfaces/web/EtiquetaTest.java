package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As etiquetas contra o banco de verdade: a V17, o nome único sem diferenciar maiúsculas, a
 * chave composta que só deixa a despesa usar etiqueta do mesmo dono, e a exclusão que limpa só
 * a ligação.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EtiquetaTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int REQUISICAO_INVALIDA = 400;
    private static final int NAO_ENCONTRADO = 404;

    private static final String PREFIXO = "etiqueta-";
    private static final String ETIQUETAS = "/api/planejamento/etiquetas";

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

    @BeforeEach
    void prepararUsuario() {
        token = criarUsuarioEObterToken();
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    /** Em ordem de nome, sem diferenciar maiúsculas, e com o nome aparado. */
    @Test
    void criaEListaPorNome() {
        criarEtiqueta("  nubank ", "ROXO");
        criarEtiqueta("Inter", "LARANJA");

        JsonNode lista = obter(ETIQUETAS);
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("nome").asString()).isEqualTo("Inter");
        assertThat(lista.get(1).get("nome").asString()).isEqualTo("nubank");
        assertThat(lista.get(1).get("cor").asString()).isEqualTo("ROXO");
        assertThat(lista.get(1).get("despesas").asLong()).isZero();
    }

    @Test
    void recusaNomeRepetidoSemDiferenciarMaiusculas() {
        long nubank = criarEtiqueta("Nubank", "ROXO");
        long inter = criarEtiqueta("Inter", "LARANJA");

        HttpResponse<String> repetida = enviar("POST", ETIQUETAS, "{\"nome\":\"NUBANK\",\"cor\":\"AZUL\"}");
        assertThat(repetida.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(repetida.body()).get("erros").get("nome").asString())
            .isEqualTo("Já existe uma etiqueta com este nome");

        // Renomear para o nome de outra é repetir; manter o próprio nome, mudando a cor, não.
        assertThat(enviar("PUT", ETIQUETAS + "/" + inter, "{\"nome\":\"nubank\",\"cor\":\"LARANJA\"}").statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
        HttpResponse<String> recolorida = enviar("PUT", ETIQUETAS + "/" + nubank, "{\"nome\":\"Nubank\",\"cor\":\"INDIGO\"}");
        assertThat(recolorida.statusCode()).as(recolorida.body()).isEqualTo(OK);
        assertThat(objectMapper.readTree(recolorida.body()).get("cor").asString()).isEqualTo("INDIGO");
    }

    @Test
    void recusaCorForaDaPaletaENomeLongo() {
        assertThat(enviar("POST", ETIQUETAS, "{\"nome\":\"Nubank\",\"cor\":\"#820AD1\"}").statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
        HttpResponse<String> longo = enviar("POST", ETIQUETAS, "{\"nome\":\"" + "a".repeat(31) + "\",\"cor\":\"ROXO\"}");
        assertThat(longo.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(longo.body()).get("erros").has("nome")).isTrue();
    }

    /** A despesa leva a etiqueta ao plano; excluí-la deixa a despesa sem etiqueta, e inteira. */
    @Test
    void excluirAEtiquetaDeixaADespesaSemEla() {
        long nubank = criarEtiqueta("Nubank", "ROXO");
        long despesa = criarDespesa(nubank);

        assertThat(obter("/api/planejamento").get("itens").get(0).get("etiquetaId").asLong()).isEqualTo(nubank);
        assertThat(obter(ETIQUETAS).get(0).get("despesas").asLong()).isEqualTo(1);

        assertThat(enviar("DELETE", ETIQUETAS + "/" + nubank, null).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode item = obter("/api/planejamento").get("itens").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(despesa);
        assertThat(item.get("etiquetaId").isNull()).isTrue();
        assertThat(obter(ETIQUETAS)).isEmpty();
    }

    @Test
    void tirarAEtiquetaDaDespesa() {
        long nubank = criarEtiqueta("Nubank", "ROXO");
        long despesa = criarDespesa(nubank);

        HttpResponse<String> editada = enviar("PUT", "/api/despesas-planejadas/" + despesa,
            "{\"descricao\":\"Fone\",\"valor\":120.00,\"prazo\":\"" + hoje.plusDays(10) + "\",\"etiquetaId\":null}");
        assertThat(editada.statusCode()).as(editada.body()).isEqualTo(OK);
        assertThat(objectMapper.readTree(editada.body()).get("etiquetaId").isNull()).isTrue();
    }

    /** A etiqueta de outra pessoa não serve: nem para marcar uma despesa, nem para editar ou excluir. */
    @Test
    void naoUsaEtiquetaDeOutroUsuario() {
        long alheia = criarEtiqueta("Nubank", "ROXO");
        token = criarUsuarioEObterToken();

        HttpResponse<String> despesa = enviar("POST", "/api/despesas-planejadas", corpoDaDespesa(alheia));
        assertThat(despesa.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(despesa.body()).get("erros").get("etiquetaId").asString())
            .isEqualTo("Etiqueta não encontrada");

        assertThat(enviar("PUT", ETIQUETAS + "/" + alheia, "{\"nome\":\"Minha\",\"cor\":\"AZUL\"}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(enviar("DELETE", ETIQUETAS + "/" + alheia, null).statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(obter(ETIQUETAS)).isEmpty();
    }

    /** O serviço confere o dono; a chave composta da V17 garante o mesmo no banco. */
    @Test
    void oBancoRecusaEtiquetaDeOutroDono() {
        long alheia = criarEtiqueta("Nubank", "ROXO");
        token = criarUsuarioEObterToken();
        long despesa = criarDespesa(null);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE despesas_planejadas SET etiqueta_id = ? WHERE id = ?", alheia, despesa
        )).hasMessageContaining("fk_despesas_planejadas_etiqueta");
    }

    private long criarEtiqueta(String nome, String cor) {
        HttpResponse<String> resposta = enviar("POST", ETIQUETAS, "{\"nome\":\"" + nome + "\",\"cor\":\"" + cor + "\"}");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private long criarDespesa(Long etiquetaId) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", corpoDaDespesa(etiquetaId));
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private String corpoDaDespesa(Long etiquetaId) {
        return "{\"descricao\":\"Fone\",\"valor\":120.00,\"prazo\":\"" + hoje.plusDays(10) + "\",\"etiquetaId\":" + etiquetaId + "}";
    }

    private JsonNode obter(String caminho) {
        HttpResponse<String> resposta = enviar("GET", caminho, null);
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);
        return objectMapper.readTree(resposta.body());
    }

    /** Ver {@code ListagemPaginadaTest}: o token é assinado aqui para não esgotar o limite do login. */
    private String criarUsuarioEObterToken() {
        return jwtService.gerarToken(ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime()));
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String corpo) {
        HttpRequest.BodyPublisher publisher = corpo == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(corpo);

        HttpRequest requisicao = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .method(metodo, publisher)
            .build();

        try {
            return clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida", excecao);
        } catch (java.io.IOException excecao) {
            throw new IllegalStateException("Falha ao chamar " + caminho, excecao);
        }
    }
}
