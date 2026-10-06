package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaTest {

    @Test
    void semanalSomaSeteDias() {
        assertThat(Recorrencia.SEMANAL.proximoPrazo(LocalDate.of(2026, 12, 28), null)).isEqualTo(LocalDate.of(2027, 1, 4));
    }

    @Test
    void mensalVenceNoMesmoDia() {
        assertThat(Recorrencia.MENSAL.proximoPrazo(LocalDate.of(2026, 10, 15), 15)).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(Recorrencia.MENSAL.proximoPrazo(LocalDate.of(2026, 12, 15), 15)).isEqualTo(LocalDate.of(2027, 1, 15));
    }

    /** O mês curto ajusta só a si mesmo: a âncora traz o 31 de volta em março. */
    @Test
    void mensalNoDia31PassaPeloFimDeFevereiroEVolta() {
        LocalDate fevereiro = Recorrencia.MENSAL.proximoPrazo(LocalDate.of(2027, 1, 31), 31);
        assertThat(fevereiro).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(Recorrencia.MENSAL.proximoPrazo(fevereiro, 31)).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(Recorrencia.MENSAL.proximoPrazo(LocalDate.of(2028, 1, 30), 30)).as("ano bissexto").isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
