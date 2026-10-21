package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.apoio.ContasDeTeste;
import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O planejamento contra o banco de verdade: a migration V11, a chave composta das contas de
 * fora, e o saldo lido de finanças pela porta.
 *
 * <p>Os lançamentos ficam dez dias atrás, e não ontem, para que o saldo de ontem e o de hoje
 * sejam os mesmos até o teste lançar algo com a data de hoje de propósito. "Hoje" é o do fuso
 * configurado — o mesmo que o servidor usa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanejamentoTest {

    private static final int OK = 200;
    private static final int CRIADO = 201;
    private static final int SEM_CONTEUDO = 204;
    private static final int REQUISICAO_INVALIDA = 400;
    private static final int NAO_ENCONTRADO = 404;
    private static final int CONFLITO = 409;

    private static final String PREFIXO = "planejamento-";

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
    private long banco;
    private long especie;
    private long categoriaReceita;
    private long categoriaDespesa;

    @BeforeEach
    void prepararCenario() {
        token = criarUsuarioEObterToken();
        banco = criarConta(token, "Banco");
        especie = criarConta(token, "Em espécie");
        categoriaReceita = extrairId(enviar("POST", "/api/categorias", token,
            "{\"nome\":\"Trabalho\",\"tipo\":\"RECEITA\"}").body());
        categoriaDespesa = extrairId(enviar("POST", "/api/categorias", token,
            "{\"nome\":\"Moradia\",\"tipo\":\"DESPESA\"}").body());

        receber(banco, "1000.00", hoje.minusDays(10));
        receber(especie, "300.00", hoje.minusDays(10));
    }

    @AfterEach
    void limparDadosDoTeste() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE usuario LIKE ?", PREFIXO + "%");
    }

    @Test
    void semContasDeForaOSaldoEOConsolidado() {
        receber(banco, "40.00", hoje);

        JsonNode plano = obter("/api/planejamento");

        assertThat(plano.get("hoje").asString()).isEqualTo(hoje.toString());
        assertThat(plano.get("saldoDeOntem").decimalValue()).isEqualByComparingTo("1300.00");
        assertThat(plano.get("saldoAtual").decimalValue()).isEqualByComparingTo("1340.00");
        assertThat(plano.get("ganhoDeHoje").decimalValue()).isEqualByComparingTo("40.00");
    }

    @Test
    void aContaDeForaSaiDoSaldo() {
        HttpResponse<String> resposta = enviar("PUT", "/api/planejamento/contas", token,
            "{\"contasDeFora\":[" + especie + "]}");
        assertThat(resposta.statusCode()).isEqualTo(OK);

        JsonNode plano = obter("/api/planejamento");
        assertThat(plano.get("saldoAtual").decimalValue()).isEqualByComparingTo("1000.00");

        JsonNode contas = obter("/api/planejamento/contas");
        assertThat(entra(contas, banco)).isTrue();
        assertThat(entra(contas, especie)).isFalse();
    }

    /** Levar dinheiro do banco para a carteira o tira do planejamento, sem ser despesa. */
    @Test
    void transferirParaAContaDeForaDiminuiOSaldoDoPlanejamento() {
        enviar("PUT", "/api/planejamento/contas", token, "{\"contasDeFora\":[" + especie + "]}");
        HttpResponse<String> transferencia = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Saque\",\"valor\":200.00,\"tipo\":\"TRANSFERENCIA\",\"contaId\":" + banco
                + ",\"contaDestinoId\":" + especie + ",\"dataTransacao\":\"" + hoje.minusDays(5) + "\"}");
        assertThat(transferencia.statusCode()).isEqualTo(CRIADO);

        assertThat(obter("/api/planejamento").get("saldoAtual").decimalValue()).isEqualByComparingTo("800.00");
    }

    @Test
    void asDespesasVemPorPrazoComAFaltaAcumulada() {
        LocalDate prazoLongo = hoje.plusDays(60);
        LocalDate prazoCurto = hoje.plusDays(30);
        criarDespesa("Cartão", "800.00", prazoLongo);
        criarDespesa("Aluguel", "1500.00", prazoCurto);

        JsonNode itens = obter("/api/planejamento").get("itens");

        assertThat(itens.get(0).get("descricao").asString()).isEqualTo("Aluguel");
        assertThat(itens.get(0).get("falta").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(itens.get(0).get("diasDeTrabalho").asInt())
            .isEqualTo(CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of()).contarDias(hoje, prazoCurto));
        assertThat(itens.get(1).get("acumulado").decimalValue()).isEqualByComparingTo("2300.00");
        assertThat(itens.get(1).get("falta").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(itens.get(1).get("situacao").asString()).isEqualTo("EM_ANDAMENTO");
    }

    @Test
    void despesaDeOutroUsuarioNaoEAlcancada() {
        long despesa = criarDespesa("Aluguel", "1500.00", hoje.plusDays(30));
        String outroToken = criarUsuarioEObterToken();

        assertThat(enviar("PUT", "/api/despesas-planejadas/" + despesa, outroToken,
            "{\"descricao\":\"Minha\",\"valor\":1.00,\"prazo\":\"" + hoje + "\"}").statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + despesa, outroToken, null).statusCode())
            .isEqualTo(NAO_ENCONTRADO);
        assertThat(obter("/api/planejamento", outroToken).get("itens")).isEmpty();
    }

    @Test
    void naoMarcaContaDeOutroUsuario() {
        String outroToken = criarUsuarioEObterToken();
        long contaAlheia = criarConta(outroToken, "Alheia");

        HttpResponse<String> resposta = enviar("PUT", "/api/planejamento/contas", token,
            "{\"contasDeFora\":[" + contaAlheia + "]}");

        assertThat(resposta.statusCode()).isEqualTo(NAO_ENCONTRADO);
    }

    @Test
    void recusaDespesaInvalidaNoCampoQueFalhou() {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            "{\"descricao\":\"Aluguel\",\"valor\":-5}");

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        JsonNode erros = objectMapper.readTree(resposta.body()).get("erros");
        assertThat(erros.has("valor")).isTrue();
        assertThat(erros.has("prazo")).isTrue();
    }

    @Test
    void editarEExcluirADespesa() {
        long despesa = criarDespesa("Aluguel", "1500.00", hoje.plusDays(30));

        HttpResponse<String> edicao = enviar("PUT", "/api/despesas-planejadas/" + despesa, token,
            "{\"descricao\":\"Aluguel de outubro\",\"valor\":1600.00,\"prazo\":\"" + hoje.plusDays(31) + "\"}");
        assertThat(edicao.statusCode()).isEqualTo(OK);
        assertThat(obter("/api/planejamento").get("itens").get(0).get("valor").decimalValue())
            .isEqualByComparingTo("1600.00");

        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + despesa, token, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);
        assertThat(obter("/api/planejamento").get("itens")).isEmpty();
    }

    /** A marca de fora não pode impedir a exclusão da conta: o CASCADE da V11 a leva junto. */
    @Test
    void excluirAContaDeForaLevaAMarcaJunto() {
        long vazia = criarConta(token, "Vazia");
        enviar("PUT", "/api/planejamento/contas", token, "{\"contasDeFora\":[" + vazia + "]}");

        assertThat(enviar("DELETE", "/api/contas/" + vazia, token, null).statusCode()).isEqualTo(SEM_CONTEUDO);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM contas_fora_do_planejamento WHERE conta_id = ?", Long.class, vazia
        )).isZero();
    }

    @Test
    void pagarLancaADespesaETiraADoPlano() {
        long despesa = criarDespesa("Aluguel", "300.00", hoje.plusDays(30));

        HttpResponse<String> pagamento = pagar(despesa, banco, categoriaDespesa, "300.00");

        assertThat(pagamento.statusCode()).isEqualTo(CRIADO);
        long transacao = objectMapper.readTree(pagamento.body()).get("transacaoId").asLong();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT tipo FROM transacoes WHERE id = ? AND conta_id = ?", String.class, transacao, banco
        )).isEqualTo("DESPESA");

        JsonNode plano = obter("/api/planejamento");
        assertThat(plano.get("itens")).isEmpty();
        assertThat(plano.get("saldoAtual").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(plano.get("ganhoDeHoje").decimalValue())
            .as("pagar hoje uma despesa planejada não é perder dinheiro hoje")
            .isEqualByComparingTo("0");
    }

    /** A transação é a prova do pagamento: sem ela, a despesa volta a ser pendente. */
    @Test
    void excluirATransacaoDePagamentoDevolveADespesaAoPlano() {
        long despesa = criarDespesa("Aluguel", "300.00", hoje.plusDays(30));
        long transacao = objectMapper.readTree(pagar(despesa, banco, categoriaDespesa, "300.00").body())
            .get("transacaoId").asLong();

        assertThat(enviar("DELETE", "/api/transacoes/" + transacao, token, null).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("id").asLong()).isEqualTo(despesa);
    }

    @Test
    void pagarDuasVezesNaoLancaDuasTransacoes() {
        long despesa = criarDespesa("Aluguel", "300.00", hoje.plusDays(30));
        pagar(despesa, banco, categoriaDespesa, "300.00");

        assertThat(pagar(despesa, banco, categoriaDespesa, "300.00").statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(contarDespesasLancadas()).isEqualTo(1);
    }

    /**
     * Recusado em finanças — conta de outro usuário, ou categoria de receita —, o pagamento
     * não deixa nada pela metade: nem transação, nem despesa marcada.
     */
    @Test
    void pagamentoRecusadoNaoFechaADespesa() {
        long despesa = criarDespesa("Aluguel", "300.00", hoje.plusDays(30));
        long contaAlheia = criarConta(criarUsuarioEObterToken(), "Alheia");

        assertThat(pagar(despesa, contaAlheia, categoriaDespesa, "300.00").statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(pagar(despesa, banco, categoriaReceita, "300.00").statusCode()).isEqualTo(REQUISICAO_INVALIDA);

        assertThat(contarDespesasLancadas()).isZero();
        assertThat(obter("/api/planejamento").get("itens")).hasSize(1);
    }

    /** O histórico mostra os dois valores, e a transação dá conta, categoria e data. */
    @Test
    void aPagaVaiParaOHistoricoComOPlanejadoEOPago() {
        long paga = criarDespesa("Aluguel", "300.00", hoje.minusDays(1));
        criarDespesa("Luz", "80.00", hoje.plusDays(5));
        long transacao = objectMapper.readTree(pagar(paga, banco, categoriaDespesa, "280.00").body())
            .get("transacaoId").asLong();

        JsonNode historico = obter("/api/despesas-planejadas/pagas");

        assertThat(historico.get("totalItens").asLong()).isEqualTo(1);
        JsonNode item = historico.get("itens").get(0);
        assertThat(item.get("id").asLong()).isEqualTo(paga);
        assertThat(item.get("descricao").asString()).isEqualTo("Aluguel");
        assertThat(item.get("valorPlanejado").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(item.get("valorPago").decimalValue()).isEqualByComparingTo("280.00");
        assertThat(item.get("dataPagamento").asString()).isEqualTo(hoje.toString());
        assertThat(item.get("transacaoId").asLong()).isEqualTo(transacao);
        assertThat(item.get("conta").asString()).isEqualTo("Banco");
        assertThat(item.get("categoria").asString()).isEqualTo("Moradia");
        assertThat(item.get("pagaComAtraso").asBoolean()).as("o prazo era ontem").isTrue();
    }

    @Test
    void oHistoricoVemDoPrazoMaisRecenteEPaginado() {
        for (int dias : new int[] {10, 30, 20}) {
            long despesa = criarDespesa("Prazo em " + dias, "100.00", hoje.plusDays(dias));
            pagar(despesa, banco, categoriaDespesa, "100.00");
        }

        JsonNode primeira = obter("/api/despesas-planejadas/pagas?tamanho=2");
        assertThat(primeira.get("totalItens").asLong()).isEqualTo(3);
        assertThat(primeira.get("totalPaginas").asInt()).isEqualTo(2);
        assertThat(primeira.get("itens")).extracting(item -> item.get("descricao").asString())
            .containsExactly("Prazo em 30", "Prazo em 20");
        assertThat(primeira.get("itens").get(0).get("pagaComAtraso").asBoolean())
            .as("pago antes do prazo").isFalse();

        JsonNode segunda = obter("/api/despesas-planejadas/pagas?tamanho=2&pagina=1");
        assertThat(segunda.get("itens")).extracting(item -> item.get("descricao").asString())
            .containsExactly("Prazo em 10");
    }

    @Test
    void oHistoricoDeOutroUsuarioNaoEAlcancado() {
        pagar(criarDespesa("Aluguel", "300.00", hoje.plusDays(30)), banco, categoriaDespesa, "300.00");

        assertThat(obter("/api/despesas-planejadas/pagas", criarUsuarioEObterToken()).get("itens")).isEmpty();
    }

    /** Desfazer o pagamento tira a despesa do histórico e a devolve ao plano: ela nunca está nos dois. */
    @Test
    void excluirATransacaoTiraADespesaDoHistorico() {
        long despesa = criarDespesa("Aluguel", "300.00", hoje.plusDays(30));
        long transacao = objectMapper.readTree(pagar(despesa, banco, categoriaDespesa, "300.00").body())
            .get("transacaoId").asLong();

        enviar("DELETE", "/api/transacoes/" + transacao, token, null);

        assertThat(obter("/api/despesas-planejadas/pagas").get("itens")).isEmpty();
    }

    @Test
    void recusaTamanhoDePaginaForaDoLimite() {
        assertThat(enviar("GET", "/api/despesas-planejadas/pagas?tamanho=0", token, null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
        assertThat(enviar("GET", "/api/despesas-planejadas/pagas?tamanho=101", token, null).statusCode())
            .isEqualTo(REQUISICAO_INVALIDA);
    }

    @Test
    void pagarARecorrenteColocaAProximaNoPlano() {
        long despesa = criarDespesa("Aluguel do carro", "600.00", hoje.plusDays(25), "SEMANAL");

        pagar(despesa, banco, categoriaDespesa, "600.00");

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        JsonNode proxima = itens.get(0);
        assertThat(proxima.get("id").asLong()).isNotEqualTo(despesa);
        assertThat(proxima.get("descricao").asString()).isEqualTo("Aluguel do carro");
        assertThat(proxima.get("valor").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(proxima.get("prazo").asString()).isEqualTo(hoje.plusDays(32).toString());
        assertThat(proxima.get("recorrencia").asString()).isEqualTo("SEMANAL");
        assertThat(obter("/api/despesas-planejadas/pagas").get("itens").get(0).get("id").asLong()).isEqualTo(despesa);
    }

    /** Desfeito o pagamento, a despesa volta; paga de novo, não nasce uma segunda sucessora. */
    @Test
    void desfazerERefazerOPagamentoNaoDuplicaAProxima() {
        long despesa = criarDespesa("Luz", "150.00", hoje.plusDays(5), "MENSAL");
        long transacao = objectMapper.readTree(pagar(despesa, banco, categoriaDespesa, "150.00").body())
            .get("transacaoId").asLong();

        enviar("DELETE", "/api/transacoes/" + transacao, token, null);
        assertThat(obter("/api/planejamento").get("itens")).as("a desfeita e a sucessora").hasSize(2);

        pagar(despesa, banco, categoriaDespesa, "150.00");
        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("prazo").asString()).isEqualTo(hoje.plusDays(5).plusMonths(1).toString());
    }

    /** Tirar a recorrência encerra a série: paga, a despesa não gera a seguinte. */
    @Test
    void semARecorrenciaOPagamentoNaoGeraProxima() {
        long despesa = criarDespesa("Luz", "150.00", hoje.plusDays(5), "MENSAL");
        HttpResponse<String> edicao = enviar("PUT", "/api/despesas-planejadas/" + despesa, token,
            "{\"descricao\":\"Luz\",\"valor\":150.00,\"prazo\":\"" + hoje.plusDays(5) + "\",\"recorrencia\":null}");
        assertThat(edicao.statusCode()).isEqualTo(OK);
        assertThat(objectMapper.readTree(edicao.body()).get("recorrencia").isNull()).isTrue();

        pagar(despesa, banco, categoriaDespesa, "150.00");

        assertThat(obter("/api/planejamento").get("itens")).isEmpty();
    }

    @Test
    void desfazerOPagamentoExcluiATransacaoEDevolveADespesaAoPlano() {
        long despesa = criarDespesa("IPVA", "300.00", hoje.plusDays(30));
        pagar(despesa, banco, categoriaDespesa, "300.00");

        HttpResponse<String> resposta = desfazer(despesa, token);

        assertThat(resposta.statusCode()).isEqualTo(OK);
        assertThat(objectMapper.readTree(resposta.body()).get("proxima").asString()).isEqualTo("NENHUMA");
        assertThat(contarDespesasLancadas()).isZero();
        assertThat(obter("/api/despesas-planejadas/pagas").get("itens")).isEmpty();
        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("id").asLong()).isEqualTo(despesa);
    }

    @Test
    void desfazerARecorrenteTiraDoPlanoAProximaComoNasceu() {
        long despesa = criarDespesa("Aluguel do carro", "600.00", hoje.plusDays(25), "SEMANAL");
        pagar(despesa, banco, categoriaDespesa, "600.00");

        HttpResponse<String> resposta = desfazer(despesa, token);

        assertThat(objectMapper.readTree(resposta.body()).get("proxima").asString()).isEqualTo("REMOVIDA");
        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("id").asLong()).isEqualTo(despesa);
    }

    /** Editada, a próxima fica: quem a ajustou decide se ainda vale. */
    @Test
    void desfazerMantemAProximaJaEditada() {
        long despesa = criarDespesa("Luz", "150.00", hoje.plusDays(5), "MENSAL");
        pagar(despesa, banco, categoriaDespesa, "150.00");
        long proxima = obter("/api/planejamento").get("itens").get(0).get("id").asLong();
        enviar("PUT", "/api/despesas-planejadas/" + proxima, token,
            "{\"descricao\":\"Luz\",\"valor\":180.00,\"prazo\":\"" + hoje.plusDays(5).plusMonths(1) + "\",\"recorrencia\":\"MENSAL\"}");

        HttpResponse<String> resposta = desfazer(despesa, token);

        assertThat(objectMapper.readTree(resposta.body()).get("proxima").asString()).isEqualTo("MANTIDA");
        assertThat(obter("/api/planejamento").get("itens")).hasSize(2);
    }

    @Test
    void naoDesfazQuandoAProximaJaFoiPaga() {
        long despesa = criarDespesa("Aluguel do carro", "100.00", hoje.plusDays(25), "SEMANAL");
        pagar(despesa, banco, categoriaDespesa, "100.00");
        long proxima = obter("/api/planejamento").get("itens").get(0).get("id").asLong();
        pagar(proxima, banco, categoriaDespesa, "100.00");

        HttpResponse<String> resposta = desfazer(despesa, token);

        assertThat(resposta.statusCode()).isEqualTo(CONFLITO);
        assertThat(contarDespesasLancadas()).isEqualTo(2);
        assertThat(obter("/api/despesas-planejadas/pagas").get("totalItens").asLong()).isEqualTo(2);
    }

    /** Pendente, ou de outro usuário: não há pagamento a desfazer, e nada é excluído. */
    @Test
    void naoDesfazOQueNaoEPagaDoUsuario() {
        long pendente = criarDespesa("IPVA", "300.00", hoje.plusDays(30));
        long paga = criarDespesa("Luz", "100.00", hoje.plusDays(30));
        pagar(paga, banco, categoriaDespesa, "100.00");

        assertThat(desfazer(pendente, token).statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(desfazer(paga, criarUsuarioEObterToken()).statusCode()).isEqualTo(NAO_ENCONTRADO);
        assertThat(contarDespesasLancadas()).isEqualTo(1);
    }

    /** Dez dias de atraso numa semanal: a atrasada, a que também já venceu e as que vencem nos próximos 30 dias. */
    @Test
    void aAtrasadaQueSeRepetePoeAsSeguintesNoPlano() {
        criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(10), "SEMANAL");

        JsonNode itens = obter("/api/planejamento").get("itens");

        assertThat(itens).extracting(item -> item.get("prazo").asString())
            .containsExactly(hoje.minusDays(10).toString(), hoje.minusDays(3).toString(), hoje.plusDays(4).toString(), hoje.plusDays(11).toString(), hoje.plusDays(18).toString(), hoje.plusDays(25).toString());
        assertThat(obter("/api/planejamento").get("itens")).as("ler de novo não gera mais").hasSize(6);
    }

    /** Duas leituras ao mesmo tempo: a trava faz a segunda esperar e achar a próxima já gerada. */
    @Test
    void leiturasSimultaneasNaoDuplicamAProxima() throws Exception {
        criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(3), "SEMANAL");

        List<CompletableFuture<HttpResponse<String>>> leituras = List.of(
            CompletableFuture.supplyAsync(() -> enviar("GET", "/api/planejamento", token, null)),
            CompletableFuture.supplyAsync(() -> enviar("GET", "/api/despesas-planejadas", token, null))
        );

        for (CompletableFuture<HttpResponse<String>> leitura : leituras) {
            assertThat(leitura.get().statusCode()).isEqualTo(OK);
        }
        assertThat(obter("/api/planejamento").get("itens")).hasSize(5);
    }

    /** Só a última dos 30 dias: a série segue, e a seguinte entra no lugar dela — sem a anterior a regerar. */
    @Test
    void excluirSoAUltimaMantemASerie() {
        criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(3), "SEMANAL");
        long ultima = obter("/api/planejamento").get("itens").get(4).get("id").asLong();

        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + ultima, token, null).statusCode()).isEqualTo(SEM_CONTEUDO);

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).extracting(item -> item.get("prazo").asString())
            .containsExactly(hoje.minusDays(3).toString(), hoje.plusDays(4).toString(), hoje.plusDays(11).toString(), hoje.plusDays(18).toString(), hoje.plusDays(32).toString());
        assertThat(itens.get(4).get("recorrencia").asString()).isEqualTo("SEMANAL");
    }

    /** A série inteira: as pendentes dos dois lados saem, e a paga fica no histórico. */
    @Test
    void excluirASerieTiraTodasAsPendentes() {
        long primeira = criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(10), "SEMANAL");
        pagar(primeira, banco, categoriaDespesa, "600.00");
        long meio = obter("/api/planejamento").get("itens").get(0).get("id").asLong();

        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + meio + "?serie=true", token, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);

        assertThat(obter("/api/planejamento").get("itens")).isEmpty();
        assertThat(obter("/api/despesas-planejadas/pagas").get("totalItens").asLong()).isEqualTo(1);
    }

    /** Reajuste e mudança de dia na série: as pendentes acompanham, a paga fica como foi. */
    @Test
    void editarASerieMudaAsPendentesENaoAPaga() {
        long primeira = criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(10), "SEMANAL");
        pagar(primeira, banco, categoriaDespesa, "600.00");
        long atrasada = obter("/api/planejamento").get("itens").get(0).get("id").asLong();

        HttpResponse<String> resposta = enviar("PUT", "/api/despesas-planejadas/" + atrasada + "?serie=true", token,
            "{\"descricao\":\"Aluguel do carro\",\"valor\":650.00,\"prazo\":\"" + hoje.minusDays(2) + "\",\"recorrencia\":\"SEMANAL\"}");

        assertThat(resposta.statusCode()).isEqualTo(OK);
        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).extracting(item -> item.get("prazo").asString())
            .containsExactly(hoje.minusDays(2).toString(), hoje.plusDays(5).toString(), hoje.plusDays(12).toString(), hoje.plusDays(19).toString(), hoje.plusDays(26).toString());
        assertThat(itens).allSatisfy(item -> assertThat(item.get("valor").decimalValue()).isEqualByComparingTo("650.00"));
        assertThat(obter("/api/despesas-planejadas/pagas").get("itens").get(0).get("valorPlanejado").decimalValue())
            .isEqualByComparingTo("600.00");
    }

    @Test
    void desfazerDaAtrasadaMantemAProxima() {
        long despesa = criarDespesa("Aluguel do carro", "600.00", hoje.minusDays(2), "SEMANAL");
        pagar(despesa, banco, categoriaDespesa, "600.00");

        HttpResponse<String> resposta = desfazer(despesa, token);

        assertThat(objectMapper.readTree(resposta.body()).get("proxima").asString()).isEqualTo("MANTIDA_NO_PERIODO");
        assertThat(obter("/api/planejamento").get("itens")).extracting(item -> item.get("prazo").asString())
            .containsExactly(hoje.minusDays(2).toString(), hoje.plusDays(5).toString(), hoje.plusDays(12).toString(), hoje.plusDays(19).toString(), hoje.plusDays(26).toString());
    }

    /**
     * A 5 de 6 semanal, vencendo hoje: o plano mostra a 5 e a 6, e para — sem parcelas, uma
     * semanal teria cinco ocorrências nos 30 dias.
     */
    @Test
    void aParceladaParaNaUltimaParcela() {
        criarParcelada("Notebook", hoje, "SEMANAL", 5, 6);

        JsonNode itens = obter("/api/planejamento").get("itens");

        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).get("parcela").asInt()).isEqualTo(5);
        assertThat(itens.get(1).get("parcela").asInt()).isEqualTo(6);
        assertThat(itens.get(1).get("totalDeParcelas").asInt()).isEqualTo(6);
        assertThat(itens.get(1).get("prazo").asString()).isEqualTo(hoje.plusDays(7).toString());
    }

    @Test
    void pagarAUltimaParcelaNaoGeraOutra() {
        long ultima = criarParcelada("Notebook", hoje.plusDays(40), "MENSAL", 6, 6);

        pagar(ultima, banco, categoriaDespesa, "300.00");

        assertThat(obter("/api/planejamento").get("itens")).isEmpty();
        JsonNode paga = obter("/api/despesas-planejadas/pagas").get("itens").get(0);
        assertThat(paga.get("parcela").asInt()).isEqualTo(6);
        assertThat(paga.get("totalDeParcelas").asInt()).isEqualTo(6);
    }

    /** De 6 para 4 parcelas na série: a 5 e a 6, pendentes, saem; a 3 e a 4 passam a ser "de 4". */
    @Test
    void reduzirOTotalNaSerieTiraAsPendentesQuePassamDaUltima() {
        long segunda = criarParcelada("Curso", hoje, "SEMANAL", 2, 6);
        assertThat(obter("/api/planejamento").get("itens")).hasSize(5);

        HttpResponse<String> resposta = enviar("PUT", "/api/despesas-planejadas/" + segunda + "?serie=true", token,
            corpoParcelado("Curso", hoje, "SEMANAL", 2, 4));
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(3);
        assertThat(itens.findValues("parcela")).extracting(JsonNode::asInt).containsExactly(2, 3, 4);
        assertThat(itens.findValues("totalDeParcelas")).extracting(JsonNode::asInt).containsOnly(4);
    }

    /** Mudar o número de uma na série renumera as outras pela posição. */
    @Test
    void mudarONumeroNaSerieRenumeraAsOutras() {
        long segunda = criarParcelada("Curso", hoje, "SEMANAL", 2, 6);

        enviar("PUT", "/api/despesas-planejadas/" + segunda + "?serie=true", token,
            corpoParcelado("Curso", hoje, "SEMANAL", 3, 6));

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens.findValues("parcela")).extracting(JsonNode::asInt).containsExactly(3, 4, 5, 6);
    }

    /** A 3 de 6 já foi paga: a série não pode passar a ter 2 parcelas. */
    @Test
    void naoReduzOTotalAbaixoDeUmaParcelaPaga() {
        long atrasada = criarParcelada("Curso", hoje.minusDays(7), "SEMANAL", 2, 6);
        long terceira = obter("/api/planejamento").get("itens").get(1).get("id").asLong();
        pagar(terceira, banco, categoriaDespesa, "100.00");

        HttpResponse<String> resposta = enviar("PUT", "/api/despesas-planejadas/" + atrasada + "?serie=true", token,
            corpoParcelado("Curso", hoje.minusDays(7), "SEMANAL", 2, 2));

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
        assertThat(objectMapper.readTree(resposta.body()).get("erros").get("totalDeParcelas").asString())
            .isEqualTo("A parcela 3 já foi paga: o total não pode ser menor que 3");
    }

    /** Excluir só a última: a anterior, pendente, vira a última, e a excluída não renasce. */
    @Test
    void excluirSoAUltimaParcelaEncerraNaAnterior() {
        criarParcelada("Notebook", hoje, "SEMANAL", 5, 6);
        long ultima = obter("/api/planejamento").get("itens").get(1).get("id").asLong();

        assertThat(enviar("DELETE", "/api/despesas-planejadas/" + ultima, token, null).statusCode())
            .isEqualTo(SEM_CONTEUDO);

        JsonNode itens = obter("/api/planejamento").get("itens");
        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).get("parcela").asInt()).isEqualTo(5);
        assertThat(itens.get(0).get("totalDeParcelas").asInt()).isEqualTo(5);
    }

    @Test
    void recusaParcelasQueNaoFecham() {
        assertThat(erroNoCampo(corpoParcelado("Notebook", hoje, "MENSAL", 7, 6))).isEqualTo("parcela");
        assertThat(erroNoCampo(corpoParcelado("Notebook", hoje, null, 2, 6))).isEqualTo("recorrencia");
        assertThat(erroNoCampo("{\"descricao\":\"Notebook\",\"valor\":300.00,\"prazo\":\"" + hoje
            + "\",\"recorrencia\":\"MENSAL\",\"parcela\":2}")).isEqualTo("totalDeParcelas");
        assertThat(erroNoCampo(corpoParcelado("Notebook", hoje, "MENSAL", 0, 6))).isEqualTo("parcela");
    }

    @Test
    void recusaRecorrenciaDesconhecida() {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            "{\"descricao\":\"IPVA\",\"valor\":100.00,\"prazo\":\"" + hoje + "\",\"recorrencia\":\"ANUAL\"}");

        assertThat(resposta.statusCode()).isEqualTo(REQUISICAO_INVALIDA);
    }

    private long criarParcelada(String descricao, LocalDate prazo, String recorrencia, int parcela, int total) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            corpoParcelado(descricao, prazo, recorrencia, parcela, total));
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(CRIADO);
        return extrairId(resposta.body());
    }

    private String corpoParcelado(String descricao, LocalDate prazo, String recorrencia, int parcela, int total) {
        String repeticao = recorrencia == null ? "null" : "\"" + recorrencia + "\"";
        return "{\"descricao\":\"" + descricao + "\",\"valor\":100.00,\"prazo\":\"" + prazo + "\",\"recorrencia\":"
            + repeticao + ",\"parcela\":" + parcela + ",\"totalDeParcelas\":" + total + "}";
    }

    /** O campo apontado pelo 400 de uma criação recusada. */
    private String erroNoCampo(String corpo) {
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token, corpo);
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(REQUISICAO_INVALIDA);
        return objectMapper.readTree(resposta.body()).get("erros").propertyNames().iterator().next();
    }

    private HttpResponse<String> desfazer(long despesa, String tokenUsado) {
        return enviar("DELETE", "/api/despesas-planejadas/" + despesa + "/pagamento", tokenUsado, null);
    }

    private HttpResponse<String> pagar(long despesa, long conta, long categoria, String valor) {
        return enviar("POST", "/api/despesas-planejadas/" + despesa + "/pagamento", token,
            "{\"descricao\":\"Pagamento\",\"valor\":" + valor + ",\"dataTransacao\":\"" + hoje
                + "\",\"categoriaId\":" + categoria + ",\"contaId\":" + conta + "}");
    }

    private long contarDespesasLancadas() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM transacoes t JOIN usuarios u ON u.id = t.usuario_id"
                + " WHERE u.usuario LIKE ? AND t.tipo = 'DESPESA'",
            Long.class, PREFIXO + "%"
        );
    }

    private boolean entra(JsonNode contas, long contaId) {
        for (JsonNode conta : contas) {
            if (conta.get("id").asLong() == contaId) {
                return conta.get("entraNoPlanejamento").asBoolean();
            }
        }
        throw new AssertionError("conta " + contaId + " fora da lista: " + contas);
    }

    private long criarConta(String tokenUsado, String nome) {
        HttpResponse<String> resposta = enviar("POST", "/api/contas", tokenUsado,
            "{\"nome\":\"" + nome + "\",\"saldoInicial\":0}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return extrairId(resposta.body());
    }

    private long criarDespesa(String descricao, String valor, LocalDate prazo) {
        return criarDespesa(descricao, valor, prazo, null);
    }

    private long criarDespesa(String descricao, String valor, LocalDate prazo, String recorrencia) {
        String repeticao = recorrencia == null ? "" : ",\"recorrencia\":\"" + recorrencia + "\"";
        HttpResponse<String> resposta = enviar("POST", "/api/despesas-planejadas", token,
            "{\"descricao\":\"" + descricao + "\",\"valor\":" + valor + ",\"prazo\":\"" + prazo + "\"" + repeticao + "}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
        return extrairId(resposta.body());
    }

    private void receber(long contaId, String valor, LocalDate data) {
        HttpResponse<String> resposta = enviar("POST", "/api/transacoes", token,
            "{\"descricao\":\"Ganho\",\"valor\":" + valor + ",\"tipo\":\"RECEITA\",\"categoriaId\":"
                + categoriaReceita + ",\"contaId\":" + contaId + ",\"dataTransacao\":\"" + data + "\"}");
        assertThat(resposta.statusCode()).isEqualTo(CRIADO);
    }

    private JsonNode obter(String caminho) {
        return obter(caminho, token);
    }

    private JsonNode obter(String caminho, String tokenUsado) {
        HttpResponse<String> resposta = enviar("GET", caminho, tokenUsado, null);
        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(OK);
        return objectMapper.readTree(resposta.body());
    }

    private long extrairId(String corpo) {
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    /** Ver {@code ListagemPaginadaTest}: o token é assinado aqui para não esgotar o limite do login. */
    private String criarUsuarioEObterToken() {
        return jwtService.gerarToken(ContasDeTeste.criar(jdbcTemplate, PREFIXO + System.nanoTime()));
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String tokenUsado, String corpo) {
        HttpRequest.BodyPublisher publisher = corpo == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(corpo);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + porta + caminho))
            .header("Content-Type", "application/json")
            .method(metodo, publisher);

        if (tokenUsado != null) {
            builder.header("Authorization", "Bearer " + tokenUsado);
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
