package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final Long CATEGORIA = 7L;
    private static final Long CONTA = 8L;
    private static final Long TRANSACAO = 99L;
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 28);

    private RepositorioDespesaPlanejada repositorio;
    private RegistroDePagamento registroDePagamento;
    private DespesaPlanejadaService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDespesaPlanejada.class);
        registroDePagamento = mock(RegistroDePagamento.class);
        servico = new DespesaPlanejadaService(repositorio, registroDePagamento);
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, USUARIO)).thenReturn(Optional.of(
            new DespesaPlanejada(DESPESA, "Aluguel", new BigDecimal("1500.00"), LocalDate.of(2026, 10, 10), USUARIO)
        ));
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, OUTRO_USUARIO)).thenReturn(Optional.empty());
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
    void pagarLancaATransacaoELigaADespesaAEla() {
        when(registroDePagamento.registrar(USUARIO, "Aluguel", new BigDecimal("1480.00"), HOJE, CATEGORIA, CONTA))
            .thenReturn(TRANSACAO);

        Long transacaoId = servico.pagar(USUARIO, DESPESA, "Aluguel", new BigDecimal("1480.00"), HOJE, CATEGORIA, CONTA);

        assertThat(transacaoId).isEqualTo(TRANSACAO);
        ArgumentCaptor<DespesaPlanejada> salva = ArgumentCaptor.forClass(DespesaPlanejada.class);
        verify(repositorio).salvar(salva.capture());
        assertThat(salva.getValue().getId()).isEqualTo(DESPESA);
        assertThat(salva.getValue().getTransacaoId()).isEqualTo(TRANSACAO);
        assertThat(salva.getValue().getValor())
            .as("o valor planejado continua o planejado; o pago mora na transação")
            .isEqualByComparingTo("1500.00");
    }

    /** Já paga, ou de outro usuário: a busca só enxerga pendentes do dono, e nada é lançado. */
    @Test
    void naoPagaDespesaQueNaoEstaPendenteParaOUsuario() {
        assertThatThrownBy(() -> servico.pagar(OUTRO_USUARIO, DESPESA, "Aluguel", BigDecimal.TEN, HOJE, CATEGORIA, CONTA))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(registroDePagamento, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    void excluiADoProprioUsuario() {
        servico.excluir(USUARIO, DESPESA);

        verify(repositorio).excluir(DESPESA);
    }
}
