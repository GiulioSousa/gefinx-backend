package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class FeriadosNacionaisTest {

    /** Datas conferidas no calendário publicado, incluindo a mais cedo e a mais tarde possíveis. */
    @Test
    void calculaAPascoa() {
        assertThat(FeriadosNacionais.pascoa(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(FeriadosNacionais.pascoa(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(FeriadosNacionais.pascoa(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(FeriadosNacionais.pascoa(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(FeriadosNacionais.pascoa(2038)).isEqualTo(LocalDate.of(2038, 4, 25));
        assertThat(FeriadosNacionais.pascoa(2285)).isEqualTo(LocalDate.of(2285, 3, 22));
    }

    @Test
    void aPaixaoDeCristoEASextaAntesDaPascoa() {
        assertThat(FeriadosNacionais.em(LocalDate.of(2026, 4, 3)))
            .hasValueSatisfying(feriado -> assertThat(feriado.nome()).isEqualTo("Paixão de Cristo"));
    }

    @Test
    void sao10FeriadosEmOrdemDeData() {
        assertThat(FeriadosNacionais.doAno(2026))
            .hasSize(10)
            .isSortedAccordingTo(Comparator.comparing(Feriado::data))
            .allSatisfy(feriado -> assertThat(feriado.data().getYear()).isEqualTo(2026));
    }

    @Test
    void aConscienciaNegraEFeriadoNacionalSoDesde2024() {
        assertThat(FeriadosNacionais.em(LocalDate.of(2024, 11, 20))).isPresent();
        assertThat(FeriadosNacionais.em(LocalDate.of(2023, 11, 20))).isEmpty();
        assertThat(FeriadosNacionais.doAno(2023)).hasSize(9);
    }

    @Test
    void diaComumNaoEFeriado() {
        assertThat(FeriadosNacionais.em(LocalDate.of(2026, 10, 13))).isEmpty();
    }
}
