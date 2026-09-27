package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DespesaPlanejadaServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long OUTRO_USUARIO = 2L;
    private static final Long DESPESA = 5L;

    private RepositorioDespesaPlanejada repositorio;
    private DespesaPlanejadaService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDespesaPlanejada.class);
        servico = new DespesaPlanejadaService(repositorio);
        when(repositorio.buscarPorIdEUsuario(DESPESA, USUARIO)).thenReturn(Optional.of(
            new DespesaPlanejada(DESPESA, "Aluguel", new BigDecimal("1500.00"), LocalDate.of(2026, 10, 10), USUARIO)
        ));
        when(repositorio.buscarPorIdEUsuario(DESPESA, OUTRO_USUARIO)).thenReturn(Optional.empty());
    }

    @Test
    void naoAtualizaDespesaDeOutroUsuario() {
        assertThatThrownBy(() -> servico.atualizar(OUTRO_USUARIO, DESPESA, "Outra", BigDecimal.TEN, LocalDate.now()))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(repositorio, never()).salvar(any());
    }

    @Test
    void naoExcluiDespesaDeOutroUsuario() {
        assertThatThrownBy(() -> servico.excluir(OUTRO_USUARIO, DESPESA))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(repositorio, never()).excluir(anyLong());
    }

    @Test
    void excluiADoProprioUsuario() {
        servico.excluir(USUARIO, DESPESA);

        verify(repositorio).excluir(DESPESA);
    }
}
