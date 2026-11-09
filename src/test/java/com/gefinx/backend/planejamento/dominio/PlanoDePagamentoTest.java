package com.gefinx.backend.planejamento.dominio;

import com.gefinx.backend.planejamento.dominio.PlanoDePagamento.ItemDoPlano;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanoDePagamentoTest {

    private static final Long USUARIO = 1L;
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 9, 27);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);
    private static final LocalDate DIA_10 = LocalDate.of(2026, 10, 10);
    private static final LocalDate DIA_20 = LocalDate.of(2026, 10, 20);

    private static final DespesaPlanejada ALUGUEL = despesa(1L, "Aluguel", "1500.00", DIA_10);
    private static final DespesaPlanejada CARTAO = despesa(2L, "Cartão", "800.00", DIA_20);

    /** R$ 50,00 por dia de trabalho, de uma janela qualquer: o cálculo da média tem os próprios testes. */
    private static final CustoDiario CINQUENTA_POR_DIA = new CustoDiario(
        SABADO.minusDays(30), SABADO.minusDays(1), new BigDecimal("1300.00"), 26, new BigDecimal("50.00"));

    @Test
    void oExemploDoPlanejamento() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", ALUGUEL, CARTAO);

        ItemDoPlano aluguel = plano.itens().get(0);
        assertThat(aluguel.acumulado()).isEqualByComparingTo("1500.00");
        assertThat(aluguel.falta()).isEqualByComparingTo("500.00");
        assertThat(aluguel.diasDeTrabalho()).isEqualTo(12);
        assertThat(aluguel.porDia()).isEqualByComparingTo("41.67");

        ItemDoPlano cartao = plano.itens().get(1);
        assertThat(cartao.acumulado()).isEqualByComparingTo("2300.00");
        assertThat(cartao.falta()).isEqualByComparingTo("1300.00");
        assertThat(cartao.diasDeTrabalho()).isEqualTo(20);
        assertThat(cartao.porDia()).isEqualByComparingTo("65.00");

        assertThat(plano.metaDiaria()).isEqualByComparingTo("65.00");
        assertThat(plano.prazoDecisivo()).isEqualTo(DIA_20);
    }

    @Test
    void asDespesasDisputamOMesmoSaldo() {
        // Comparada sozinha com o saldo, cada uma pareceria coberta.
        PlanoDePagamento plano = calcular(SABADO, "1000.00",
            despesa(1L, "Primeira", "800.00", DIA_10),
            despesa(2L, "Segunda", "800.00", DIA_20));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.COBERTA);
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("0");
        assertThat(plano.itens().get(1).situacao()).isEqualTo(SituacaoDaDespesa.EM_ANDAMENTO);
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("600.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("30.00");
    }

    @Test
    void oSaldoEConsumidoPelaOrdemDoPrazoENaoPelaDeEntrada() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", CARTAO, ALUGUEL);

        assertThat(plano.itens()).extracting(item -> item.despesa().getDescricao())
            .containsExactly("Aluguel", "Cartão");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("500.00");
    }

    @Test
    void aMetaArredondaParaCimaNoCentavo() {
        // Segunda, terça e quarta: três dias até a quinta. 100 / 3 = 33,333...
        PlanoDePagamento plano = calcular(SEGUNDA, "0", despesa(1L, "Conta", "100.00", SEGUNDA.plusDays(3)));

        assertThat(plano.metaDiaria()).isEqualByComparingTo("33.34");
    }

    /**
     * O exemplo que define a meta que acompanha os lançamentos: 300 em três dias pede 100 por
     * dia. Ganhos 100 hoje, faltam 200 para o resto de hoje e os dois dias seguintes.
     */
    @Test
    void aMetaAcompanhaAReceitaDoDia() {
        DespesaPlanejada conta = despesa(1L, "Conta", "300.00", SEGUNDA.plusDays(3));

        assertThat(calcular(SEGUNDA, "0", conta).metaDiaria()).isEqualByComparingTo("100.00");
        assertThat(calcular(SEGUNDA, "100.00", conta).metaDiaria()).isEqualByComparingTo("66.67");
    }

    @Test
    void umaDespesaForaDoPlanoSobeAMeta() {
        PlanoDePagamento plano = calcular(SABADO, "950.00", ALUGUEL, CARTAO);

        // (2300 - 950) / 20 = 67,50, contra 65,00 com o saldo de 1000.
        assertThat(plano.metaDiaria()).isEqualByComparingTo("67.50");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("550.00");
    }

    @Test
    void cobrirAsDespesasZeraAMeta() {
        PlanoDePagamento plano = calcular(SABADO, "1600.00", ALUGUEL);

        ItemDoPlano aluguel = plano.itens().get(0);
        assertThat(aluguel.situacao()).isEqualTo(SituacaoDaDespesa.COBERTA);
        assertThat(aluguel.porDia()).isNull();
        assertThat(plano.metaDiaria()).isEqualByComparingTo("0");
        assertThat(plano.prazoDecisivo()).isNull();
    }

    @Test
    void asReceitasDeHojeVemComoChegaramEAMetaNaoAsUsa() {
        PlanoDePagamento plano = PlanoDePagamento.calcular(SABADO, new BigDecimal("1000.00"),
            new BigDecimal("1000.00"), new BigDecimal("180.00"), null, List.of(ALUGUEL, CARTAO),
            CalendarioDeTrabalho.SEGUNDA_A_SABADO);

        assertThat(plano.receitasDeHoje()).isEqualByComparingTo("180.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("65.00");
    }

    @Test
    void oCustoDiarioSomaNaMeta() {
        PlanoDePagamento plano = calcularComCusto(SABADO, "1000.00", "1000.00", CINQUENTA_POR_DIA, ALUGUEL, CARTAO);

        assertThat(plano.metaDiaria()).isEqualByComparingTo("115.00");
        assertThat(plano.custoDiario()).isEqualTo(CINQUENTA_POR_DIA);
        assertThat(plano.itens().get(1).porDia())
            .as("o por dia da despesa continua só dela")
            .isEqualByComparingTo("65.00");
    }

    @Test
    void semDespesaPlanejadaOCustoDiarioEAMetaInteira() {
        PlanoDePagamento plano = calcularComCusto(SABADO, "1000.00", "1000.00", CINQUENTA_POR_DIA);

        assertThat(plano.metaDiaria()).isEqualByComparingTo("50.00");
        assertThat(plano.prazoDecisivo()).isNull();
    }

    /**
     * Gastos 50 de combustível hoje, o saldo foi a 950. Esse gasto já está na meta pela média:
     * o saldo da meta o devolve, e ela continua 65 + 50. A falta, que é dinheiro de verdade,
     * usa o saldo de agora.
     */
    @Test
    void oGastoDeHojeNoCustoDiarioNaoSobeAMeta() {
        PlanoDePagamento plano = calcularComCusto(SABADO, "950.00", "1000.00", CINQUENTA_POR_DIA, ALUGUEL, CARTAO);

        assertThat(plano.metaDiaria()).isEqualByComparingTo("115.00");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("550.00");
        assertThat(plano.saldoAtual()).isEqualByComparingTo("950.00");
    }

    @Test
    void naFolgaOCustoDiarioContinuaNaMeta() {
        PlanoDePagamento plano = calcularComCusto(DOMINGO, "1000.00", "1000.00", CINQUENTA_POR_DIA, ALUGUEL);

        // 500 em 11 dias = 45,46, mais os 50 do custo: a meta dos próximos dias de trabalho.
        assertThat(plano.folgaDeHoje()).isEqualTo("Domingo");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("95.46");
    }

    @Test
    void semDiaDeTrabalhoAntesDoPrazoEntraInteiraNaMeta() {
        PlanoDePagamento plano = calcular(SABADO, "100.00", despesa(3L, "Vence hoje", "300.00", SABADO), CARTAO);

        ItemDoPlano venceHoje = plano.itens().get(0);
        assertThat(venceHoje.situacao()).isEqualTo(SituacaoDaDespesa.VENCE_HOJE);
        assertThat(venceHoje.falta()).isEqualByComparingTo("200.00");
        assertThat(venceHoje.diasDeTrabalho()).isZero();
        assertThat(venceHoje.porDia()).isNull();

        // O cartão só enxerga o que sobra depois dela, 300 + 800 - 100 = 1000, e divide pelos
        // 20 dias só o que fica depois do pendente: (1000 - 200) / 20 = 40.
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("1000.00");
        assertThat(plano.itens().get(1).porDia()).isEqualByComparingTo("40.00");
        assertThat(plano.pendente()).isEqualByComparingTo("200.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("240.00");
        assertThat(plano.prazoDecisivo()).isEqualTo(DIA_20);
    }

    /**
     * O prazo de hoje manda no selo, mesmo com o saldo cobrindo: é hoje que ela tem de ser
     * paga, e "coberta" diria para deixar quieto justamente no último dia.
     */
    @Test
    void oPrazoDeHojeVemAntesDeCoberta() {
        PlanoDePagamento plano = calcular(SABADO, "500.00", despesa(3L, "Vence hoje", "300.00", SABADO));

        ItemDoPlano venceHoje = plano.itens().get(0);
        assertThat(venceHoje.situacao()).isEqualTo(SituacaoDaDespesa.VENCE_HOJE);
        assertThat(venceHoje.falta()).isEqualByComparingTo("0");
        assertThat(venceHoje.porDia()).isNull();
    }

    /**
     * Sem dia de trabalho até o prazo continua existindo, e agora quer dizer só isso: o prazo
     * ainda não chegou, mas não sobra dia útil antes dele.
     */
    @Test
    void semDiaDeTrabalhoNaoSeConfundeComVenceHoje() {
        PlanoDePagamento plano = calcular(DOMINGO, "0", despesa(1L, "Conta", "100.00", SEGUNDA));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.SEM_DIAS_DE_TRABALHO);
    }

    /** Sem outra despesa, o pendente é a meta inteira, e não há prazo para dividir. */
    @Test
    void oPendenteSozinhoEAMeta() {
        PlanoDePagamento plano = calcular(SABADO, "100.00", despesa(3L, "Vence hoje", "300.00", SABADO));

        assertThat(plano.metaDiaria()).isEqualByComparingTo("200.00");
        assertThat(plano.pendente()).isEqualByComparingTo("200.00");
        assertThat(plano.prazoDecisivo()).isNull();
    }

    @Test
    void domingoComPrazoNaSegundaNaoTemDiaDeTrabalho() {
        PlanoDePagamento plano = calcular(DOMINGO, "0", despesa(1L, "Conta", "100.00", SEGUNDA));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.SEM_DIAS_DE_TRABALHO);
        assertThat(plano.metaDiaria()).isEqualByComparingTo("100.00");
    }

    /** Como o resto da meta, o pendente acompanha o saldo: o que se ganha hoje já o diminui. */
    @Test
    void oPendenteAcompanhaOSaldo() {
        PlanoDePagamento plano = calcular(SABADO, "250.00", despesa(3L, "Atrasada", "300.00", SABADO.minusDays(3)));

        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("50.00");
        assertThat(plano.pendente()).isEqualByComparingTo("50.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("50.00");
    }

    /** No domingo a meta vale para os próximos dias de trabalho. */
    @Test
    void naFolgaAMetaEDosProximosDiasEDizOMotivo() {
        PlanoDePagamento plano = calcular(DOMINGO, "1000.00", ALUGUEL);

        assertThat(plano.folgaDeHoje()).isEqualTo("Domingo");
        // 500 em 11 dias, de segunda 28/09 à véspera de 10/10: o domingo de hoje não entra.
        assertThat(plano.metaDiaria()).isEqualByComparingTo("45.46");
    }

    @Test
    void emDiaDeTrabalhoNaoHaFolga() {
        assertThat(calcular(SABADO, "1000.00", ALUGUEL).folgaDeHoje()).isNull();
    }

    /**
     * 12/10/2026 é segunda e feriado: de sábado 26/09 até a véspera de 20/10 são 19 dias de
     * trabalho, e não 20. Ignorado, ele volta a contar.
     */
    @Test
    void oFeriadoTiraUmDiaDaContaEIgnoradoDevolve() {
        LocalDate aparecida = LocalDate.of(2026, 10, 12);

        PlanoDePagamento comFeriado = calcularCom(
            CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of()), CARTAO);
        PlanoDePagamento ignorado = calcularCom(
            CalendarioDeTrabalho.comFeriadosNacionais(Set.of(aparecida), List.of()), CARTAO);

        assertThat(comFeriado.itens().get(0).diasDeTrabalho()).isEqualTo(19);
        assertThat(ignorado.itens().get(0).diasDeTrabalho()).isEqualTo(20);
    }

    @Test
    void aAtrasadaConsomeSaldoPrimeiro() {
        PlanoDePagamento plano = calcular(SABADO, "100.00",
            despesa(3L, "Atrasada", "300.00", SABADO.minusDays(6)), CARTAO);

        ItemDoPlano atrasada = plano.itens().get(0);
        assertThat(atrasada.situacao()).isEqualTo(SituacaoDaDespesa.ATRASADA);
        assertThat(atrasada.falta()).isEqualByComparingTo("200.00");
        assertThat(atrasada.porDia()).isNull();
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("1000.00");
        assertThat(plano.pendente()).isEqualByComparingTo("200.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("240.00");
    }

    /**
     * Paga pela conta principal, ela sai da lista e o saldo vai de 100 a -200. A dívida passa a
     * ser saldo negativo, dividido pelos dias do cartão: (800 + 200) / 20 = 50.
     */
    @Test
    void pagaAAtrasadaOPendenteSaiDaMeta() {
        PlanoDePagamento plano = calcular(SABADO, "-200.00", CARTAO);

        assertThat(plano.pendente()).isEqualByComparingTo("0");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("50.00");
    }

    @Test
    void atrasadaContinuaAtrasadaMesmoComSaldoParaEla() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", despesa(3L, "Atrasada", "300.00", SABADO.minusDays(1)));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.ATRASADA);
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("0");
        assertThat(plano.pendente())
            .as("o saldo já cobre: falta pagar, mas não falta ganhar")
            .isEqualByComparingTo("0");
    }

    @Test
    void saldoNegativoAumentaAFalta() {
        PlanoDePagamento plano = calcular(SABADO, "-200.00", ALUGUEL);

        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("1700.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("141.67");
    }

    @Test
    void semDespesasNemCustoNaoHaMeta() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00");

        assertThat(plano.itens()).isEmpty();
        assertThat(plano.metaDiaria()).isEqualByComparingTo("0");
        assertThat(plano.custoDiario()).isNull();
        assertThat(plano.prazoDecisivo()).isNull();
    }

    /** Sem custo diário e sem gasto de hoje a devolver: os dois saldos são o mesmo. */
    private static PlanoDePagamento calcular(LocalDate hoje, String saldo, DespesaPlanejada... despesas) {
        return calcularComCusto(hoje, saldo, saldo, null, despesas);
    }

    private static PlanoDePagamento calcularComCusto(
        LocalDate hoje, String saldoAtual, String saldoDaMeta, CustoDiario custo, DespesaPlanejada... despesas
    ) {
        return PlanoDePagamento.calcular(
            hoje, new BigDecimal(saldoAtual), new BigDecimal(saldoDaMeta), BigDecimal.ZERO, custo,
            List.of(despesas), CalendarioDeTrabalho.SEGUNDA_A_SABADO
        );
    }

    private static PlanoDePagamento calcularCom(CalendarioDeTrabalho calendario, DespesaPlanejada... despesas) {
        BigDecimal saldo = new BigDecimal("1000.00");
        return PlanoDePagamento.calcular(SABADO, saldo, saldo, BigDecimal.ZERO, null, List.of(despesas), calendario);
    }

    private static DespesaPlanejada despesa(Long id, String descricao, String valor, LocalDate prazo) {
        return new DespesaPlanejada(id, descricao, new BigDecimal(valor), prazo, USUARIO);
    }
}
