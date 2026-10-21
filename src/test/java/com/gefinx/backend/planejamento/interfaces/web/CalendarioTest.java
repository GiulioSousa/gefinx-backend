package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O calendário de trabalho contra o banco de verdade: a migration V15, o INSERT com ON
 * CONFLICT, e o período mudando os dias que o plano conta.
 *
 * <p>Os feriados são lidos num ano fixo, e não no de hoje, para o teste não mudar de
 * resultado com a data em que roda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarioTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int REQUISICAO_INVALIDA = 400;
    private static final int NAO_ENCONTRADO = 404;

    private static final String PREFIXO = "calendario-";
    private static final String APARECIDA_2030 = "2030-10-12";
    private static final String DOMINGO_2030 = "2030-01-06";

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

    /** Ignorar e voltar a considerar podem se repetir: o segundo pedido não vira erro. */
    @Test
    void ignorarEConsiderarUmFeriado() {
        assertThat(ignorado(obter("/api/planejamento/calendario?ano=2030"), APARECIDA_2030)).isFalse();

        assertThat(enviar("PUT", rotaDoFeriado(APARECIDA_2030), null).statusCode()).isEqualTo(SEM_CONTEUDO);
        assertThat(enviar("PUT", rotaDoFeriado(APARECIDA_2030), null).statusCode()).isEqualTo(SEM_CONTEUDO);
        JsonNode calendario = obter("/api/planejamento/calendario?ano=2030");
        assertThat(calendario.get("feriados")).hasSize(10);
        assertThat(ignorado(calendario, APARECIDA_2030)).isTrue();

        assertThat(enviar("DELETE", rotaDoFeriado(APARECIDA_2030), null).statusCode()).isEqualTo(SEM_CONTEUDO);
        assertThat(enviar("DELETE", rotaDoFeriado(APARECIDA_2030), null).statusCode()).isEqualTo(SEM_CONTEUDO);
        assertThat(ignorado(obter("/api/planejamento/calendario?ano=2030"), APARECIDA_2030)).isFalse();
    }

    @Test
    void naoIgnoraDiaQueNaoEFeriado() {
        assertThat(enviar("PUT", rotaDoFeriado("2030-10-14"), null).statusCode()).isEqualTo(NAO_ENCONTRADO);
    }

    /** O período tira dias da conta do plano, e excluí-lo os devolve. */
    @Test
    void oPeriodoMudaOsDiasDeTrabalhoDoPlano() {
        LocalDate prazo = hoje.plusDays(40);
        LocalDate inicio = hoje.plusDays(10);
        LocalDate fim = hoje.plusDays(16);
        criarDespesa(prazo);

        HttpResponse<String> criado = enviar("POST", "/api/planejamento/calendario/periodos",
            "{\"inicio\":\"" + inicio + "\",\"fim\":\"" + fim + "\",\"descricao\":\"Férias\"}");
        assertThat(criado.statusCode()).as(criado.body()).isEqualTo(CRIADO);
        long periodo = objectMapper.readTree(criado.body()).get("id").asLong();

        CalendarioDeTrabalho esperado = CalendarioDeTrabalho.comFeriadosNacionais(
            Set.of(), List.of(new PeriodoSemTrabalho(periodo, inicio, fim, "Férias"))
        );
        assertThat(diasDeTrabalhoDaDespesa()).isEqualTo(esperado.contarDias(hoje, prazo));
        assertThat(obter("/api/planejamento/calendario?ano=" + inicio.getYear()).get("periodos").get(0)
            .get("descricao").asString()).isEqualTo("Férias");

        assertThat(enviar("DELETE", "/api/planejamento/calendario/periodos/" + periodo, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);
        assertThat(diasDeTrabalhoDaDespesa())
            .isEqualTo(CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of()).contarDias(hoje, prazo));
    }

    @Test
    void recusaOPeriodoNoCampoQueFalhou() {
        HttpResponse<String> domingo = enviar("POST", "/api/planejamento/calendario/periodos",
            "{\"inicio\":\"" + DOMINGO_2030 + "\"}");
        assertThat(domingo.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(domingo.body()).get("erros").get("inicio").asString())
            .isEqualTo("Este dia já não é de trabalho: Domingo");

        HttpResponse<String> semInicio = enviar("POST", "/api/planejamento/calendario/periodos", "{}");
        assertThat(semInicio.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(semInicio.body()).get("erros").has("inicio")).isTrue();
    }

    @Test
    void periodoDeOutroUsuarioNaoEAlcancado() {
        HttpResponse<String> criado = enviar("POST", "/api/planejamento/calendario/periodos",
            "{\"inicio\":\"2030-01-07\"}");
        long periodo = objectMapper.readTree(criado.body()).get("id").asLong();
        String meuToken = token;
        token = criarUsuarioEObterToken();

        assertThat(enviar("DELETE", "/api/planejamento/calendario/periodos/" + periodo, null).statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(obter("/api/planejamento/calendario?ano=2030").get("periodos")).isEmpty();

        token = meuToken;
        assertThat(enviar("DELETE", "/api/planejamento/calendario/periodos/" + periodo, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);
    }

    @Test
    void recusaAnoForaDoLimite() {
        assertThat(enviar("GET", "/api/planejamento/calendario?ano=1999", null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
    }

    private String rotaDoFeriado(String data) {
        return "/api/planejamento/calendario/feriados-ignorados/" + data;
    }

    private boolean ignorado(JsonNode calendario, String data) {
        for (JsonNode feriado : calendario.get("feriados")) {
            if (feriado.get("data").asString().equals(data)) {
                return feriado.get("ignorado").asBoolean();
            }
        }
        throw new AssertionError(data + " fora da lista: " + calendario);
    }

    private void criarDespesa(LocalDate prazo) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas",
            "{\"descricao\":\"Cartão\",\"valor\":500.00,\"prazo\":\"" + prazo + "\"}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private int diasDeTrabalhoDaDespesa() {
        return obter("/api/planejamento").get("itens").get(0).get("diasDeTrabalho").asInt();
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
