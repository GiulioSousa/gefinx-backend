package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.aplicacao.CalendarioService.CalendarioDoAno;
import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;
import com.gefinx.backend.planejamento.dominio.RepositorioFeriadosIgnorados;
import com.gefinx.backend.planejamento.dominio.RepositorioPeriodosSemTrabalho;
import com.gefinx.backend.planejamento.dominio.excecoes.PeriodoSemTrabalhoRecusadoException;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsSecondArg;

class CalendarioServiceTest {

    private static final Long USUARIO = 1L;
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);
    private static final LocalDate APARECIDA = LocalDate.of(2026, 10, 12);

    private RepositorioFeriadosIgnorados feriadosIgnorados;
    private RepositorioPeriodosSemTrabalho periodos;
    private CalendarioService servico;

    @BeforeEach
    void preparar() {
        feriadosIgnorados = mock(RepositorioFeriadosIgnorados.class);
        periodos = mock(RepositorioPeriodosSemTrabalho.class);
        servico = new CalendarioService(feriadosIgnorados, periodos);
        when(feriadosIgnorados.listarPorUsuario(USUARIO)).thenReturn(Set.of());
        when(periodos.listarPorUsuario(USUARIO)).thenReturn(List.of());
        when(periodos.salvar(eq(USUARIO), any())).then(returnsSecondArg());
    }

    @Test
    void semFimEUmDiaSoEADescricaoEmBrancoVaiComoAusente() {
        PeriodoSemTrabalho periodo = servico.adicionarPeriodo(USUARIO, SEGUNDA, null, "   ");

        assertThat(periodo.inicio()).isEqualTo(SEGUNDA);
        assertThat(periodo.fim()).isEqualTo(SEGUNDA);
        assertThat(periodo.descricao()).isNull();
    }

    @Test
    void recusaOFimAntesDoInicio() {
        assertRecusado(() -> servico.adicionarPeriodo(USUARIO, SEGUNDA, SEGUNDA.minusDays(1), null), "fim");
    }

    @Test
    void recusaPeriodoDeMaisDeUmAno() {
        assertRecusado(() -> servico.adicionarPeriodo(USUARIO, SEGUNDA, SEGUNDA.plusDays(366), null), "fim");
        assertThat(servico.adicionarPeriodo(USUARIO, SEGUNDA, SEGUNDA.plusDays(365), null)).isNotNull();
    }

    @Test
    void recusaOQueSeSobrepoeAOutroPeriodo() {
        when(periodos.listarPorUsuario(USUARIO)).thenReturn(List.of(
            new PeriodoSemTrabalho(9L, SEGUNDA, SEGUNDA.plusDays(4), "Férias")
        ));

        assertThatThrownBy(() -> servico.adicionarPeriodo(USUARIO, SEGUNDA.plusDays(4), SEGUNDA.plusDays(6), null))
            .isInstanceOf(PeriodoSemTrabalhoRecusadoException.class)
            .hasMessageContaining("Férias");
        assertThat(servico.adicionarPeriodo(USUARIO, SEGUNDA.plusDays(5), null, null)).isNotNull();
    }

    /** Domingo e feriado já ficam fora da meta: guardá-los pareceria valer e não valeria. */
    @Test
    void recusaODiaQueJaNaoEDeTrabalho() {
        assertThatThrownBy(() -> servico.adicionarPeriodo(USUARIO, DOMINGO, null, null))
            .isInstanceOf(PeriodoSemTrabalhoRecusadoException.class)
            .hasMessageContaining("Domingo");
        assertThatThrownBy(() -> servico.adicionarPeriodo(USUARIO, APARECIDA, null, null))
            .hasMessageContaining("Nossa Senhora Aparecida");
        verify(periodos, never()).salvar(anyLong(), any());
    }

    @Test
    void aceitaOFeriadoIgnorado() {
        when(feriadosIgnorados.listarPorUsuario(USUARIO)).thenReturn(Set.of(APARECIDA));

        assertThat(servico.adicionarPeriodo(USUARIO, APARECIDA, null, "Folga")).isNotNull();
    }

    @Test
    void soIgnoraFeriadoDeVerdade() {
        assertThatThrownBy(() -> servico.ignorarFeriado(USUARIO, SEGUNDA))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(feriadosIgnorados, never()).ignorar(any(), any());

        servico.ignorarFeriado(USUARIO, APARECIDA);
        verify(feriadosIgnorados).ignorar(USUARIO, APARECIDA);
    }

    @Test
    void excluirPeriodoQueNaoEDoUsuarioDa404() {
        when(periodos.excluir(USUARIO, 9L)).thenReturn(false);

        assertThatThrownBy(() -> servico.excluirPeriodo(USUARIO, 9L))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
    }

    @Test
    void oAnoTrazOsFeriadosMarcadosEOsPeriodosQueOTocam() {
        when(feriadosIgnorados.listarPorUsuario(USUARIO)).thenReturn(Set.of(APARECIDA));
        PeriodoSemTrabalho recesso = new PeriodoSemTrabalho(1L, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 2), null);
        PeriodoSemTrabalho deOutroAno = new PeriodoSemTrabalho(2L, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 8), null);
        when(periodos.listarPorUsuario(USUARIO)).thenReturn(List.of(recesso, deOutroAno));

        CalendarioDoAno ano = servico.doAno(USUARIO, 2026);

        assertThat(ano.feriados()).hasSize(10);
        assertThat(ano.feriados()).filteredOn(CalendarioService.FeriadoDoAno::ignorado)
            .singleElement()
            .satisfies(item -> assertThat(item.feriado().data()).isEqualTo(APARECIDA));
        assertThat(ano.periodos()).containsExactly(recesso);
    }

    private static void assertRecusado(org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada, String campo) {
        assertThatThrownBy(chamada)
            .isInstanceOf(PeriodoSemTrabalhoRecusadoException.class)
            .extracting("campo").isEqualTo(campo);
    }
}
