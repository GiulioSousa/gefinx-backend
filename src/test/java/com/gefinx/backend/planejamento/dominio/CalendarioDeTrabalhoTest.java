package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioDeTrabalhoTest {

    private static final CalendarioDeTrabalho CALENDARIO = CalendarioDeTrabalho.SEGUNDA_A_SABADO;
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 9, 27);
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);

    @Test
    void umaSemanaInteiraTemSeisDias() {
        assertThat(CALENDARIO.contarDias(SEGUNDA, SEGUNDA.plusWeeks(1))).isEqualTo(6);
    }

    @Test
    void domingoNaoConta() {
        assertThat(CALENDARIO.contarDias(DOMINGO, SEGUNDA)).isZero();
    }

    @Test
    void oInicioContaEOFimNao() {
        assertThat(CALENDARIO.contarDias(SABADO, DOMINGO)).isEqualTo(1);
        assertThat(CALENDARIO.contarDias(SABADO, SEGUNDA)).isEqualTo(1);
        assertThat(CALENDARIO.contarDias(SABADO, SEGUNDA.plusDays(1))).isEqualTo(2);
    }

    @Test
    void intervaloVazioOuInvertidoDaZero() {
        assertThat(CALENDARIO.contarDias(SABADO, SABADO)).isZero();
        assertThat(CALENDARIO.contarDias(SEGUNDA, SABADO)).isZero();
    }

    @Test
    void contaOsDiasDoExemploDoPlanejamento() {
        // De sábado 26/09 até a véspera de sábado 10/10, e até a véspera de terça 20/10.
        assertThat(CALENDARIO.contarDias(SABADO, LocalDate.of(2026, 10, 10))).isEqualTo(12);
        assertThat(CALENDARIO.contarDias(SABADO, LocalDate.of(2026, 10, 20))).isEqualTo(20);
    }

    @Test
    void aContaPorSemanasBateComAContagemDiaADia() {
        for (int inicio = 0; inicio < 7; inicio++) {
            LocalDate de = SEGUNDA.plusDays(inicio);
            for (int duracao = 0; duracao <= 400; duracao++) {
                LocalDate ate = de.plusDays(duracao);
                assertThat(CALENDARIO.contarDias(de, ate))
                    .as("de %s a %s", de, ate)
                    .isEqualTo(contarUmAUm(de, ate));
            }
        }
    }

    private static int contarUmAUm(LocalDate de, LocalDate ate) {
        int dias = 0;
        for (LocalDate dia = de; dia.isBefore(ate); dia = dia.plusDays(1)) {
            if (dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                dias++;
            }
        }
        return dias;
    }
}
