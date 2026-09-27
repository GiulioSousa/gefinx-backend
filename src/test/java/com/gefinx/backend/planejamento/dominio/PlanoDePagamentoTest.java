package com.gefinx.backend.planejamento.dominio;

import com.gefinx.backend.planejamento.dominio.PlanoDePagamento.ItemDoPlano;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    @Test
    void oExemploDoPlanejamento() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1000.00", ALUGUEL, CARTAO);

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
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1000.00",
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
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1000.00", CARTAO, ALUGUEL);

        assertThat(plano.itens()).extracting(item -> item.despesa().getDescricao())
            .containsExactly("Aluguel", "Cartão");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("500.00");
    }

    @Test
    void aMetaArredondaParaCimaNoCentavo() {
        // Segunda, terça e quarta: três dias até a quinta. 100 / 3 = 33,333...
        PlanoDePagamento plano = calcular(SEGUNDA, "0", "0", despesa(1L, "Conta", "100.00", SEGUNDA.plusDays(3)));

        assertThat(plano.metaDiaria()).isEqualByComparingTo("33.34");
    }

    @Test
    void oGanhoDeHojeEADiferencaEntreOSaldoDeAgoraEODeOntem() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1040.00", ALUGUEL, CARTAO);

        assertThat(plano.ganhoDeHoje()).isEqualByComparingTo("40.00");
        assertThat(plano.restanteHoje()).isEqualByComparingTo("25.00");
    }

    @Test
    void aMetaFicaParadaDuranteODiaEAFaltaNao() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1040.00", ALUGUEL, CARTAO);

        assertThat(plano.metaDiaria()).isEqualByComparingTo("65.00");
        assertThat(plano.itens().get(1).porDia()).isEqualByComparingTo("65.00");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("460.00");
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("1260.00");
    }

    @Test
    void ganharMaisQueAMetaZeraORestanteDeHoje() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1100.00", ALUGUEL, CARTAO);

        assertThat(plano.restanteHoje()).isEqualByComparingTo("0");
    }

    @Test
    void umDiaNegativoAumentaORestanteDeHoje() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "950.00", ALUGUEL, CARTAO);

        assertThat(plano.ganhoDeHoje()).isEqualByComparingTo("-50.00");
        assertThat(plano.restanteHoje()).isEqualByComparingTo("115.00");
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("550.00");
    }

    @Test
    void cobrirUmaDespesaNoMeioDoDiaNaoBaixaAMeta() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1600.00", ALUGUEL);

        ItemDoPlano aluguel = plano.itens().get(0);
        assertThat(aluguel.situacao()).isEqualTo(SituacaoDaDespesa.COBERTA);
        assertThat(aluguel.porDia()).isNull();
        assertThat(plano.metaDiaria()).isEqualByComparingTo("41.67");
        assertThat(plano.restanteHoje()).isEqualByComparingTo("0");
    }

    @Test
    void semDiaDeTrabalhoAntesDoPrazoFicaForaDaMetaMasConsomeSaldo() {
        PlanoDePagamento plano = calcular(SABADO, "100.00", "100.00",
            despesa(3L, "Vence hoje", "300.00", SABADO), CARTAO);

        ItemDoPlano venceHoje = plano.itens().get(0);
        assertThat(venceHoje.situacao()).isEqualTo(SituacaoDaDespesa.SEM_DIAS_DE_TRABALHO);
        assertThat(venceHoje.falta()).isEqualByComparingTo("200.00");
        assertThat(venceHoje.diasDeTrabalho()).isZero();
        assertThat(venceHoje.porDia()).isNull();

        // O cartão só enxerga o que sobra depois dela: 300 + 800 - 100 = 1000, em 20 dias.
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("1000.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("50.00");
        assertThat(plano.prazoDecisivo()).isEqualTo(DIA_20);
    }

    @Test
    void domingoComPrazoNaSegundaNaoTemDiaDeTrabalho() {
        PlanoDePagamento plano = calcular(DOMINGO, "0", "0", despesa(1L, "Conta", "100.00", SEGUNDA));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.SEM_DIAS_DE_TRABALHO);
        assertThat(plano.metaDiaria()).isEqualByComparingTo("0");
    }

    @Test
    void aAtrasadaConsomeSaldoPrimeiro() {
        PlanoDePagamento plano = calcular(SABADO, "100.00", "100.00",
            despesa(3L, "Atrasada", "300.00", SABADO.minusDays(6)), CARTAO);

        ItemDoPlano atrasada = plano.itens().get(0);
        assertThat(atrasada.situacao()).isEqualTo(SituacaoDaDespesa.ATRASADA);
        assertThat(atrasada.falta()).isEqualByComparingTo("200.00");
        assertThat(atrasada.porDia()).isNull();
        assertThat(plano.itens().get(1).falta()).isEqualByComparingTo("1000.00");
    }

    @Test
    void atrasadaContinuaAtrasadaMesmoComSaldoParaEla() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1000.00",
            despesa(3L, "Atrasada", "300.00", SABADO.minusDays(1)));

        assertThat(plano.itens().get(0).situacao()).isEqualTo(SituacaoDaDespesa.ATRASADA);
        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("0");
    }

    @Test
    void saldoNegativoAumentaAFalta() {
        PlanoDePagamento plano = calcular(SABADO, "-200.00", "-200.00", ALUGUEL);

        assertThat(plano.itens().get(0).falta()).isEqualByComparingTo("1700.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("141.67");
    }

    @Test
    void semDespesasNaoHaMeta() {
        PlanoDePagamento plano = calcular(SABADO, "1000.00", "1000.00");

        assertThat(plano.itens()).isEmpty();
        assertThat(plano.metaDiaria()).isEqualByComparingTo("0");
        assertThat(plano.restanteHoje()).isEqualByComparingTo("0");
        assertThat(plano.prazoDecisivo()).isNull();
    }

    private static PlanoDePagamento calcular(LocalDate hoje, String saldoDeOntem, String saldoAtual, DespesaPlanejada... despesas) {
        return PlanoDePagamento.calcular(
            hoje, new BigDecimal(saldoDeOntem), new BigDecimal(saldoAtual), List.of(despesas),
            CalendarioDeTrabalho.SEGUNDA_A_SABADO
        );
    }

    private static DespesaPlanejada despesa(Long id, String descricao, String valor, LocalDate prazo) {
        return new DespesaPlanejada(id, descricao, new BigDecimal(valor), prazo, USUARIO);
    }
}
