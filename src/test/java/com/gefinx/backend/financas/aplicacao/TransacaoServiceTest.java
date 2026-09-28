package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.SaldoNoDia;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.financas.dominio.excecoes.SaldoNegativoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long CATEGORIA = 10L;
    private static final Long CONTA = 20L;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 20);

    private RepositorioTransacao repositorioTransacao;
    private RepositorioCategoria repositorioCategoria;
    private RepositorioConta repositorioConta;
    private TransacaoService servico;

    @BeforeEach
    void preparar() {
        repositorioTransacao = mock(RepositorioTransacao.class);
        repositorioCategoria = mock(RepositorioCategoria.class);
        repositorioConta = mock(RepositorioConta.class);
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO))
            .thenReturn(Optional.of(new Conta(CONTA, "Conta principal", USUARIO)));
        servico = new TransacaoService(repositorioTransacao, repositorioCategoria, repositorioConta);
    }

    @Test
    void recusaTransacaoCujoTipoDivergeDaCategoria() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");

        assertThatThrownBy(() -> criar(TipoTransacao.RECEITA))
            .isInstanceOf(TipoIncompativelComCategoriaException.class)
            .hasMessageContaining("Alimentação")
            .hasMessageContaining("DESPESA");
    }

    @Test
    void naoGravaNadaQuandoOsTiposDivergem() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");

        assertThatThrownBy(() -> criar(TipoTransacao.RECEITA))
            .isInstanceOf(TipoIncompativelComCategoriaException.class);

        verify(repositorioTransacao, never()).salvar(any());
    }

    @Test
    void aceitaTransacaoDoMesmoTipoDaCategoria() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        assertThatCode(() -> criar(TipoTransacao.DESPESA)).doesNotThrowAnyException();
    }

    @Test
    void tambemValidaOTipoNaAtualizacao() {
        categoriaExistente(TipoTransacao.RECEITA, "Salário");
        when(repositorioTransacao.buscarPorIdEUsuario(5L, USUARIO))
            .thenReturn(Optional.of(transacaoExistente()));

        assertThatThrownBy(() -> servico.atualizar(
            USUARIO, 5L, "descricao", BigDecimal.TEN, TipoTransacao.DESPESA, CATEGORIA, CONTA, null, DATA
        )).isInstanceOf(TipoIncompativelComCategoriaException.class);
    }

    @Test
    void continuaRecusandoCategoriaDeOutroUsuario() {
        when(repositorioCategoria.buscarPorIdEUsuario(CATEGORIA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criar(TipoTransacao.DESPESA))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private void criar(TipoTransacao tipo) {
        servico.criar(USUARIO, "descricao", BigDecimal.TEN, tipo, CATEGORIA, CONTA, null, DATA);
    }

    private void categoriaExistente(TipoTransacao tipo, String nome) {
        when(repositorioCategoria.buscarPorIdEUsuario(CATEGORIA, USUARIO))
            .thenReturn(Optional.of(new Categoria(CATEGORIA, nome, tipo, USUARIO)));
    }

    private Transacao transacaoExistente() {
        return Transacao.nova("antiga", BigDecimal.ONE, TipoTransacao.RECEITA, DATA, CATEGORIA, CONTA, USUARIO);
    }

    /**
     * O banco recusaria de qualquer forma, pela chave estrangeira composta da V7 — mas de
     * lá viria um erro interno, e o cliente tem de receber "não encontrada".
     */
    @Test
    void recusaContaDeOutroUsuario() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criar(TipoTransacao.DESPESA))
            .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(repositorioTransacao, never()).salvar(any());
    }

    @Test
    void gravaTransferenciaSemCategoriaEComContaDeDestino() {
        Long contaDestino = 21L;
        when(repositorioConta.buscarPorIdEUsuario(contaDestino, USUARIO))
            .thenReturn(Optional.of(new Conta(contaDestino, "Carteira", USUARIO)));
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        servico.criar(
            USUARIO, "Passando dinheiro", BigDecimal.TEN, TipoTransacao.TRANSFERENCIA,
            null, CONTA, contaDestino, DATA
        );

        org.mockito.ArgumentCaptor<Transacao> capturada = org.mockito.ArgumentCaptor.forClass(Transacao.class);
        verify(repositorioTransacao).salvar(capturada.capture());
        Transacao gravada = capturada.getValue();
        assertThat(gravada.getTipo()).isEqualTo(TipoTransacao.TRANSFERENCIA);
        assertThat(gravada.getCategoriaId()).isNull();
        assertThat(gravada.getContaId()).isEqualTo(CONTA);
        assertThat(gravada.getContaDestinoId()).isEqualTo(contaDestino);
    }

    /** Transferência não consulta categoria: não tem uma, e exigir a busca seria ruído. */
    @Test
    void aTransferenciaNaoConsultaCategoria() {
        Long contaDestino = 21L;
        when(repositorioConta.buscarPorIdEUsuario(contaDestino, USUARIO))
            .thenReturn(Optional.of(new Conta(contaDestino, "Carteira", USUARIO)));
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        servico.criar(
            USUARIO, "Passando dinheiro", BigDecimal.TEN, TipoTransacao.TRANSFERENCIA,
            null, CONTA, contaDestino, DATA
        );

        verify(repositorioCategoria, never()).buscarPorIdEUsuario(any(), any());
    }

    @Test
    void recusaTransferenciaParaContaDeOutroUsuario() {
        Long contaDeOutro = 99L;
        when(repositorioConta.buscarPorIdEUsuario(contaDeOutro, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.criar(
            USUARIO, "Passando dinheiro", BigDecimal.TEN, TipoTransacao.TRANSFERENCIA,
            null, CONTA, contaDeOutro, DATA
        )).isInstanceOf(RecursoNaoEncontradoException.class);

        verify(repositorioTransacao, never()).salvar(any());
    }

    /**
     * Editar uma transferência para virar despesa tem de limpar a conta de destino, e o
     * contrário tem de limpar a categoria. Repassar o campo do formato anterior deixaria
     * uma linha híbrida, que as CHECKs da V8 recusariam como erro de banco.
     */
    @Test
    void aoVirarDespesaAEdicaoLimpaAContaDeDestino() {
        when(repositorioTransacao.buscarPorIdEUsuario(5L, USUARIO))
            .thenReturn(Optional.of(transacaoExistente()));
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        servico.atualizar(
            USUARIO, 5L, "virou despesa", BigDecimal.TEN, TipoTransacao.DESPESA,
            CATEGORIA, CONTA, 21L, DATA
        );

        org.mockito.ArgumentCaptor<Transacao> capturada = org.mockito.ArgumentCaptor.forClass(Transacao.class);
        verify(repositorioTransacao).salvar(capturada.capture());
        assertThat(capturada.getValue().getContaDestinoId()).isNull();
        assertThat(capturada.getValue().getCategoriaId()).isEqualTo(CATEGORIA);
    }

    // Saldo nunca negativo (Etapa 26). A conta dia a dia é do banco — ver SaldoNaoNegativoTest;
    // aqui fica o que o serviço faz com a resposta: travar antes, conferir depois, recusar.

    @Test
    void recusaLancamentoQueDeixaAContaNegativaEmAlgumDia() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));
        when(repositorioTransacao.primeiroDiaNegativo(CONTA))
            .thenReturn(Optional.of(new SaldoNoDia(DATA, new BigDecimal("-5.00"))));

        assertThatThrownBy(() -> criar(TipoTransacao.DESPESA))
            .isInstanceOf(SaldoNegativoException.class)
            .hasMessageContaining("Conta principal")
            .hasMessageContaining("20/08/2026")
            .hasMessageContaining("5,00");
    }

    @Test
    void travaAContaAntesDeGravarEConfereDepois() {
        categoriaExistente(TipoTransacao.DESPESA, "Alimentação");
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        criar(TipoTransacao.DESPESA);

        InOrder ordem = inOrder(repositorioConta, repositorioTransacao);
        ordem.verify(repositorioConta).travar(Set.of(CONTA));
        ordem.verify(repositorioTransacao).salvar(any());
        ordem.verify(repositorioTransacao).primeiroDiaNegativo(CONTA);
    }

    /** Trocar a conta tira o dinheiro de onde ele estava: a conta antiga é a que pode estourar. */
    @Test
    void aEdicaoTravaEConfereAContaAntigaEANova() {
        Long outraConta = 21L;
        categoriaExistente(TipoTransacao.RECEITA, "Salário");
        when(repositorioConta.buscarPorIdEUsuario(outraConta, USUARIO))
            .thenReturn(Optional.of(new Conta(outraConta, "Poupança", USUARIO)));
        when(repositorioTransacao.buscarPorIdEUsuario(1L, USUARIO)).thenReturn(Optional.of(transacaoExistente()));
        when(repositorioTransacao.salvar(any(Transacao.class))).thenAnswer(c -> c.getArgument(0));

        servico.atualizar(USUARIO, 1L, "movida", BigDecimal.ONE, TipoTransacao.RECEITA, CATEGORIA, outraConta, null, DATA);

        verify(repositorioConta).travar(Set.of(CONTA, outraConta));
        verify(repositorioTransacao).primeiroDiaNegativo(CONTA);
        verify(repositorioTransacao).primeiroDiaNegativo(outraConta);
    }

    @Test
    void excluirUmaReceitaTambemConfereOSaldo() {
        when(repositorioTransacao.buscarPorIdEUsuario(1L, USUARIO)).thenReturn(Optional.of(transacaoExistente()));
        when(repositorioTransacao.primeiroDiaNegativo(CONTA))
            .thenReturn(Optional.of(new SaldoNoDia(DATA, new BigDecimal("-1.00"))));

        assertThatThrownBy(() -> servico.excluir(USUARIO, 1L)).isInstanceOf(SaldoNegativoException.class);
        verify(repositorioConta).travar(Set.of(CONTA));
    }
}
