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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As etiquetas contra o banco de verdade: a V17 e a V19, o nome único sem diferenciar
 * maiúsculas, as chaves compostas que só deixam a despesa usar etiqueta do mesmo dono, o limite
 * de três por despesa, e a exclusão que apaga só a ligação.
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

    /**
     * A despesa leva as etiquetas ao plano, e cada uma a conta. Excluir uma tira só ela: a
     * despesa fica inteira, com a outra.
     */
    @Test
    void excluirUmaEtiquetaDeixaADespesaComAsOutras() {
        long nubank = criarEtiqueta("Nubank", "ROXO");
        long viagem = criarEtiqueta("Viagem", "AZUL");
        long despesa = criarDespesa(viagem, nubank);

        assertThat(etiquetasDe(obter("/api/planejamento").get("itens").get(0))).containsExactly(nubank, viagem);
        assertThat(obter(ETIQUETAS)).allSatisfy(etiqueta -> assertThat(etiqueta.get("despesas").asLong()).isEqualTo(1));

        assertThat(enviar("DELETE", ETIQUETAS + "/" + nubank, null).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode item = obter("/api/planejamento").get("itens").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(despesa);
        assertThat(etiquetasDe(item)).containsExactly(viagem);
        assertThat(obter(ETIQUETAS)).hasSize(1);
    }

    /** Lista vazia, nula ou ausente: as três tiram todas. */
    @Test
    void tirarAsEtiquetasDaDespesa() {
        long nubank = criarEtiqueta("Nubank", "ROXO");
        long despesa = criarDespesa(nubank);

        HttpResponse<String> editada = enviar("PUT", "/api/despesas-planejadas/" + despesa,
            "{\"descricao\":\"Fone\",\"valor\":120.00,\"prazo\":\"" + hoje.plusDays(10) + "\",\"etiquetaIds\":[]}");
        assertThat(editada.statusCode()).as(editada.body()).isEqualTo(OK);
        assertThat(etiquetasDe(objectMapper.readTree(editada.body()))).isEmpty();
        assertThat(obter(ETIQUETAS).get(0).get("despesas").asLong()).isZero();
    }

    /** A quarta é recusada no campo, e nada muda; a repetida conta uma vez. */
    @Test
    void recusaMaisDeTresEtiquetas() {
        long[] ids = {
            criarEtiqueta("Nubank", "ROXO"), criarEtiqueta("Inter", "LARANJA"),
            criarEtiqueta("Viagem", "AZUL"), criarEtiqueta("Casa", "VERDE")
        };

        HttpResponse<String> quatro = enviar("POST", "/api/despesas-planejadas", corpoDaDespesa(ids[0], ids[1], ids[2], ids[3]));
        assertThat(quatro.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(quatro.body()).get("erros").get("etiquetaIds").asString())
            .isEqualTo("Escolha no máximo 3 etiquetas por despesa");

        long despesa = criarDespesa(ids[0], ids[1], ids[2], ids[2]);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM despesas_planejadas_etiquetas WHERE despesa_id = ?", Long.class, despesa
        )).isEqualTo(3);
    }

    /** A etiqueta de outra pessoa não serve: nem para marcar uma despesa, nem para editar ou excluir. */
    @Test
    void naoUsaEtiquetaDeOutroUsuario() {
        long alheia = criarEtiqueta("Nubank", "ROXO");
        token = criarUsuarioEObterToken();
        long minha = criarEtiqueta("Inter", "LARANJA");

        HttpResponse<String> despesa = enviar("POST", "/api/despesas-planejadas", corpoDaDespesa(minha, alheia));
        assertThat(despesa.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(despesa.body()).get("erros").get("etiquetaIds").asString())
            .isEqualTo("Etiqueta não encontrada");

        assertThat(enviar("PUT", ETIQUETAS + "/" + alheia, "{\"nome\":\"Minha\",\"cor\":\"AZUL\"}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(enviar("DELETE", ETIQUETAS + "/" + alheia, null).statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(obter(ETIQUETAS)).hasSize(1);
    }

    /** O serviço confere o dono; as chaves compostas da V19 garantem o mesmo no banco. */
    @Test
    void oBancoRecusaEtiquetaDeOutroDono() {
        long alheia = criarEtiqueta("Nubank", "ROXO");
        token = criarUsuarioEObterToken();
        long despesa = criarDespesa();
        Long dono = jdbcTemplate.queryForObject("SELECT usuario_id FROM despesas_planejadas WHERE id = ?", Long.class, despesa);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO despesas_planejadas_etiquetas (despesa_id, etiqueta_id, usuario_id) VALUES (?, ?, ?)",
            despesa, alheia, dono
        )).hasMessageContaining("fk_despesas_planejadas_etiquetas_etiqueta");
    }

    private long criarEtiqueta(String nome, String cor) {
        HttpResponse<String> resposta = enviar("POST", ETIQUETAS, "{\"nome\":\"" + nome + "\",\"cor\":\"" + cor + "\"}");
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private long criarDespesa(long... etiquetaIds) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", corpoDaDespesa(etiquetaIds));
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
        return objectMapper.readTree(resposta.body()).get("id").asLong();
    }

    private String corpoDaDespesa(long... etiquetaIds) {
        String lista = Arrays.stream(etiquetaIds).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return "{\"descricao\":\"Fone\",\"valor\":120.00,\"prazo\":\"" + hoje.plusDays(10) + "\",\"etiquetaIds\":[" + lista + "]}";
    }

    private static List<Long> etiquetasDe(JsonNode despesa) {
        List<Long> ids = new ArrayList<>();
        despesa.get("etiquetaIds").forEach(id -> ids.add(id.asLong()));
        return ids;
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
