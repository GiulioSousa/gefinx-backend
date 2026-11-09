package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustoDiarioTest {

    /** Segunda. A janela vai de sábado 29/08 a domingo 27/09: 30 dias, 25 de trabalho. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);

    @Test
    void aJanelaSaoOsTrintaDiasAteOntem() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, List.of(), CalendarioDeTrabalho.SEGUNDA_A_SABADO);

        assertThat(custo.inicio()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(custo.fim()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    /** Come-se no domingo também, mas é nos dias de trabalho que se ganha para isso. */
    @Test
    void divideOTotalPelosDiasDeTrabalhoDaJanela() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, valores("600.00", "400.00"),
            CalendarioDeTrabalho.SEGUNDA_A_SABADO);

        assertThat(custo.total()).isEqualByComparingTo("1000.00");
        assertThat(custo.diasDeTrabalho()).isEqualTo(25);
        assertThat(custo.porDia()).isEqualByComparingTo("40.00");
    }

    @Test
    void arredondaParaCimaNoCentavo() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, valores("1000.01"), CalendarioDeTrabalho.SEGUNDA_A_SABADO);

        assertThat(custo.porDia()).isEqualByComparingTo("40.01");
    }

    /** 07/09/2026, Independência, é segunda: um dia a menos para dividir. */
    @Test
    void oFeriadoDaJanelaTiraUmDia() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, valores("960.00"),
            CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of()));

        assertThat(custo.diasDeTrabalho()).isEqualTo(24);
        assertThat(custo.porDia()).isEqualByComparingTo("40.00");
    }

    @Test
    void semGastoOPorDiaEZero() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, List.of(), CalendarioDeTrabalho.SEGUNDA_A_SABADO);

        assertThat(custo.total()).isEqualByComparingTo("0");
        assertThat(custo.porDia()).isEqualByComparingTo("0");
    }

    /** Férias cobrindo a janela inteira: não há por quantos dias dividir, e o por dia não divide por zero. */
    @Test
    void semDiaDeTrabalhoNaJanelaOPorDiaEZero() {
        CalendarioDeTrabalho deFerias = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of(
            new PeriodoSemTrabalho(1L, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 30), "Férias")
        ));

        CustoDiario custo = CustoDiario.calcular(SEGUNDA, valores("500.00"), deFerias);

        assertThat(custo.diasDeTrabalho()).isZero();
        assertThat(custo.porDia()).isEqualByComparingTo("0");
    }

    private static List<BigDecimal> valores(String... valores) {
        return java.util.Arrays.stream(valores).map(BigDecimal::new).toList();
    }
}
