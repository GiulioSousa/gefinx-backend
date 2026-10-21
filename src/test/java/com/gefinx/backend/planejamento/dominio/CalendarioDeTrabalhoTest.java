package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    @Test
    void oFeriadoEmDiaDeSemanaSaiDaConta() {
        CalendarioDeTrabalho comFeriados = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of());

        // 12/10/2026 é segunda.
        assertThat(comFeriados.contarDias(SABADO, LocalDate.of(2026, 10, 20))).isEqualTo(19);
        assertThat(comFeriados.motivoDaFolga(LocalDate.of(2026, 10, 12))).contains("Nossa Senhora Aparecida");
    }

    @Test
    void oFeriadoIgnoradoVoltaAContar() {
        LocalDate aparecida = LocalDate.of(2026, 10, 12);
        CalendarioDeTrabalho ignorando = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(aparecida), List.of());

        assertThat(ignorando.contarDias(SABADO, LocalDate.of(2026, 10, 20))).isEqualTo(20);
        assertThat(ignorando.motivoDaFolga(aparecida)).isEmpty();
    }

    /** 15/11/2026 é domingo: o feriado não tira um segundo dia. */
    @Test
    void oFeriadoNoDomingoNaoTiraDiaDeNovo() {
        CalendarioDeTrabalho comFeriados = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of());
        LocalDate segundaAntes = LocalDate.of(2026, 11, 9);

        assertThat(comFeriados.contarDias(segundaAntes, segundaAntes.plusWeeks(1))).isEqualTo(6);
        assertThat(comFeriados.motivoDaFolga(LocalDate.of(2026, 11, 15))).contains("Proclamação da República");
    }

    /** De 31/12/2026, quinta, a 04/01/2027: o 01/01 é sexta e fica de fora. */
    @Test
    void contaOFeriadoDoAnoSeguinte() {
        CalendarioDeTrabalho comFeriados = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of());

        assertThat(comFeriados.contarDias(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 4))).isEqualTo(2);
    }

    @Test
    void oPeriodoTiraSoOsDiasDeTrabalhoDentroDele() {
        // De segunda 28/09 a domingo 04/10: seis dias de trabalho e um domingo.
        PeriodoSemTrabalho semana = new PeriodoSemTrabalho(1L, SEGUNDA, SEGUNDA.plusDays(6), "Férias");
        CalendarioDeTrabalho comFerias = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of(semana));

        assertThat(comFerias.contarDias(SABADO, SEGUNDA.plusWeeks(1).plusDays(1))).isEqualTo(2);
        assertThat(comFerias.motivoDaFolga(SEGUNDA.plusDays(2))).contains("Férias");
    }

    /**
     * As férias de 10 a 16/10/2026 contêm o 12/10: o feriado sai uma vez só. De 05 a 18/10
     * sobram a semana de 05 a 09 e o sábado 17.
     */
    @Test
    void oFeriadoDentroDoPeriodoNaoSaiDuasVezes() {
        PeriodoSemTrabalho ferias = new PeriodoSemTrabalho(1L, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16), null);
        CalendarioDeTrabalho comFerias = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(), List.of(ferias));

        assertThat(comFerias.contarDias(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 19))).isEqualTo(6);
        assertThat(comFerias.motivoDaFolga(LocalDate.of(2026, 10, 12)))
            .as("o nome do feriado vem antes do período").contains("Nossa Senhora Aparecida");
        assertThat(comFerias.motivoDaFolga(LocalDate.of(2026, 10, 13))).contains("Dia sem trabalho");
    }

    @Test
    void oDomingoTemNome() {
        assertThat(CALENDARIO.motivoDaFolga(DOMINGO)).contains("Domingo");
        assertThat(CALENDARIO.motivoDaFolga(SEGUNDA)).isEmpty();
    }

    /** A conta por semanas, menos as folgas, bate com olhar dia por dia — num ano cheio de feriados. */
    @Test
    void comFolgasAContaBateComAContagemDiaADia() {
        LocalDate aparecida = LocalDate.of(2026, 10, 12);
        CalendarioDeTrabalho calendario = CalendarioDeTrabalho.comFeriadosNacionais(Set.of(aparecida), List.of(
            new PeriodoSemTrabalho(1L, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 24), "Férias"),
            new PeriodoSemTrabalho(2L, LocalDate.of(2026, 12, 24), LocalDate.of(2027, 1, 2), "Recesso")
        ));
        LocalDate de = LocalDate.of(2026, 1, 1);
        for (int inicio = 0; inicio < 400; inicio += 3) {
            for (int duracao = 0; duracao <= 120; duracao += 7) {
                LocalDate primeiro = de.plusDays(inicio);
                LocalDate ate = primeiro.plusDays(duracao);
                assertThat(calendario.contarDias(primeiro, ate))
                    .as("de %s a %s", primeiro, ate)
                    .isEqualTo(contarUmAUm(calendario, primeiro, ate));
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

    private static int contarUmAUm(CalendarioDeTrabalho calendario, LocalDate de, LocalDate ate) {
        int dias = 0;
        for (LocalDate dia = de; dia.isBefore(ate); dia = dia.plusDays(1)) {
            if (calendario.motivoDaFolga(dia).isEmpty()) {
                dias++;
            }
        }
        return dias;
    }
}
