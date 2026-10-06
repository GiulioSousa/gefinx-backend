package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DespesaPaga;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.PagamentoLancado;
import com.gefinx.backend.planejamento.dominio.Pagina;
import com.gefinx.backend.planejamento.dominio.Recorrencia;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private FonteDeSaldo fonteDeSaldo;
    private DespesaPlanejadaService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioDespesaPlanejada.class);
        registroDePagamento = mock(RegistroDePagamento.class);
        fonteDeSaldo = mock(FonteDeSaldo.class);
        servico = new DespesaPlanejadaService(repositorio, registroDePagamento, fonteDeSaldo);
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, USUARIO)).thenReturn(Optional.of(
            new DespesaPlanejada(DESPESA, "Aluguel", new BigDecimal("1500.00"), LocalDate.of(2026, 10, 10), USUARIO)
        ));
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, OUTRO_USUARIO)).thenReturn(Optional.empty());
    }

    @Test
    void naoAtualizaDespesaDeOutroUsuario() {
        assertThatThrownBy(() -> servico.atualizar(OUTRO_USUARIO, DESPESA, "Outra", BigDecimal.TEN, LocalDate.now(), null))
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

    /**
     * Cada paga recebe a sua transação, numa leitura só de finanças. A que perdeu a transação
     * entre as duas leituras continua na página, sem os dados do pagamento.
     */
    @Test
    void oHistoricoJuntaCadaPagaASuaTransacao() {
        DespesaPlanejada aluguel = new DespesaPlanejada(1L, "Aluguel", new BigDecimal("1500.00"), HOJE, USUARIO, 10L);
        DespesaPlanejada luz = new DespesaPlanejada(2L, "Luz", new BigDecimal("80.00"), HOJE, USUARIO, 20L);
        when(repositorio.listarPagasPorUsuario(USUARIO, 0, 20)).thenReturn(new Pagina<>(List.of(aluguel, luz), 0, 20, 2));
        when(fonteDeSaldo.pagamentos(USUARIO, List.of(10L, 20L))).thenReturn(List.of(
            new PagamentoLancado(10L, new BigDecimal("1480.00"), HOJE.plusDays(1), "Banco", "Moradia")
        ));

        Pagina<DespesaPaga> historico = servico.listarPagas(USUARIO, 0, 20);

        assertThat(historico.totalItens()).isEqualTo(2);
        DespesaPaga primeira = historico.itens().get(0);
        assertThat(primeira.pagamento()).hasValueSatisfying(pagamento ->
            assertThat(pagamento.valor()).isEqualByComparingTo("1480.00"));
        assertThat(primeira.pagaComAtraso()).as("pago um dia depois do prazo").isTrue();
        assertThat(historico.itens().get(1).pagamento()).isEmpty();
        assertThat(historico.itens().get(1).pagaComAtraso()).isFalse();
    }

    @Test
    void pagarNoDiaDoPrazoNaoEAtraso() {
        DespesaPlanejada despesa = new DespesaPlanejada(1L, "Aluguel", BigDecimal.TEN, HOJE, USUARIO, 10L);

        DespesaPaga paga = new DespesaPaga(despesa, Optional.of(new PagamentoLancado(10L, BigDecimal.TEN, HOJE, "Banco", "Moradia")));

        assertThat(paga.pagaComAtraso()).isFalse();
    }

    @Test
    void pagarARecorrenteColocaAProximaNoPlano() {
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, USUARIO)).thenReturn(Optional.of(new DespesaPlanejada(
            DESPESA, "Aluguel do carro", new BigDecimal("600.00"), HOJE, USUARIO, null, Recorrencia.SEMANAL, null, null
        )));
        when(registroDePagamento.registrar(any(), any(), any(), any(), any(), any())).thenReturn(TRANSACAO);

        servico.pagar(USUARIO, DESPESA, "Aluguel do carro", new BigDecimal("600.00"), HOJE, CATEGORIA, CONTA);

        ArgumentCaptor<DespesaPlanejada> salvas = ArgumentCaptor.forClass(DespesaPlanejada.class);
        verify(repositorio, times(2)).salvar(salvas.capture());
        assertThat(salvas.getAllValues().get(0).getTransacaoId()).isEqualTo(TRANSACAO);
        DespesaPlanejada proxima = salvas.getAllValues().get(1);
        assertThat(proxima.getPrazo()).isEqualTo(HOJE.plusWeeks(1));
        assertThat(proxima.getAnteriorId()).isEqualTo(DESPESA);
    }

    /** Pagamento desfeito e refeito: a sucessora gerada da primeira vez continua valendo. */
    @Test
    void naoGeraUmaSegundaProxima() {
        when(repositorio.buscarPendentePorIdEUsuario(DESPESA, USUARIO)).thenReturn(Optional.of(new DespesaPlanejada(
            DESPESA, "Aluguel do carro", new BigDecimal("600.00"), HOJE, USUARIO, null, Recorrencia.SEMANAL, null, null
        )));
        when(repositorio.temSucessora(DESPESA)).thenReturn(true);

        servico.pagar(USUARIO, DESPESA, "Aluguel do carro", new BigDecimal("600.00"), HOJE, CATEGORIA, CONTA);

        verify(repositorio, times(1)).salvar(any());
    }

    @Test
    void pagarAAvulsaNaoGeraProxima() {
        servico.pagar(USUARIO, DESPESA, "Aluguel", new BigDecimal("1500.00"), HOJE, CATEGORIA, CONTA);

        verify(repositorio, times(1)).salvar(any());
        verify(repositorio, never()).temSucessora(anyLong());
    }

    @Test
    void excluiADoProprioUsuario() {
        servico.excluir(USUARIO, DESPESA);

        verify(repositorio).excluir(DESPESA);
    }
}
