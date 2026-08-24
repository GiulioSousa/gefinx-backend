package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.ContaDuplicadaException;
import com.gefinx.backend.financas.dominio.excecoes.ContaEmUsoException;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContaServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long CONTA = 20L;
    private static final Long CATEGORIA_ABERTURA_ID = 99L;

    private RepositorioConta repositorioConta;
    private RepositorioTransacao repositorioTransacao;
    private RepositorioCategoria repositorioCategoria;
    private ContaService servico;

    @BeforeEach
    void preparar() {
        repositorioConta = mock(RepositorioConta.class);
        repositorioTransacao = mock(RepositorioTransacao.class);
        repositorioCategoria = mock(RepositorioCategoria.class);
        servico = new ContaService(repositorioConta, repositorioTransacao, repositorioCategoria);

        when(repositorioConta.salvar(any(Conta.class)))
            .thenAnswer(c -> {
                Conta enviada = c.getArgument(0);
                return new Conta(CONTA, enviada.getNome(), enviada.getUsuarioId());
            });
    }

    @Test
    void criaTransacaoDeAberturaQuandoHaSaldoInicial() {
        categoriaDeAberturaInexistente();

        servico.criar(USUARIO, "Carteira", new BigDecimal("500.00"));

        Transacao abertura = aberturaGravada();
        assertThat(abertura.getValor()).isEqualByComparingTo("500.00");
        assertThat(abertura.getTipo()).isEqualTo(TipoTransacao.RECEITA);
        assertThat(abertura.getUsuarioId()).isEqualTo(USUARIO);
    }

    /**
     * O ponto que um erro de fiação esconderia: a abertura tem de cair na conta que
     * acabou de nascer. Numa conta errada ela ainda somaria no consolidado, e o total
     * bateria — o estrago só apareceria na leitura por conta.
     */
    @Test
    void aAberturaCaiNaContaRecemCriada() {
        categoriaDeAberturaInexistente();

        servico.criar(USUARIO, "Carteira", new BigDecimal("500.00"));

        assertThat(aberturaGravada().getContaId()).isEqualTo(CONTA);
    }

    @Test
    void naoCriaAberturaQuandoOSaldoInicialEZero() {
        servico.criar(USUARIO, "Carteira", BigDecimal.ZERO);

        verify(repositorioTransacao, never()).salvar(any());
    }

    @Test
    void naoCriaAberturaQuandoOSaldoInicialNaoFoiInformado() {
        servico.criar(USUARIO, "Carteira", null);

        verify(repositorioTransacao, never()).salvar(any());
    }

    /**
     * Criar a categoria de abertura a cada conta esbarraria no UNIQUE (nome, tipo,
     * usuario_id) e derrubaria a segunda conta com saldo inicial.
     */
    @Test
    void reaproveitaACategoriaDeAberturaQuandoElaJaExiste() {
        when(repositorioCategoria.buscarPorNomeTipoUsuario(ContaService.CATEGORIA_ABERTURA, TipoTransacao.RECEITA, USUARIO))
            .thenReturn(Optional.of(new Categoria(CATEGORIA_ABERTURA_ID, ContaService.CATEGORIA_ABERTURA, TipoTransacao.RECEITA, USUARIO)));

        servico.criar(USUARIO, "Carteira", new BigDecimal("10.00"));

        verify(repositorioCategoria, never()).salvar(any());
        assertThat(aberturaGravada().getCategoriaId()).isEqualTo(CATEGORIA_ABERTURA_ID);
    }

    @Test
    void recusaExcluirContaComTransacoes() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO))
            .thenReturn(Optional.of(new Conta(CONTA, "Carteira", USUARIO)));
        when(repositorioTransacao.existePorConta(CONTA)).thenReturn(true);

        assertThatThrownBy(() -> servico.excluir(USUARIO, CONTA))
            .isInstanceOf(ContaEmUsoException.class);

        verify(repositorioConta, never()).excluir(any());
    }

    @Test
    void excluiContaSemTransacoes() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO))
            .thenReturn(Optional.of(new Conta(CONTA, "Carteira", USUARIO)));
        when(repositorioTransacao.existePorConta(CONTA)).thenReturn(false);

        servico.excluir(USUARIO, CONTA);

        verify(repositorioConta).excluir(CONTA);
    }

    /** Conta de outro dono responde "não encontrada": confirmar que existe já vaza dado. */
    @Test
    void trataContaDeOutroUsuarioComoInexistente() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.excluir(USUARIO, CONTA))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void recusaNomeDeContaJaUsadoPeloMesmoUsuario() {
        when(repositorioConta.existePorNomeEUsuario("Carteira", USUARIO)).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(USUARIO, "Carteira", null))
            .isInstanceOf(ContaDuplicadaException.class)
            .hasMessageContaining("Carteira");
    }

    /** O mesmo nome que a migration V7 deu às contas do backfill. */
    @Test
    void aContaPadraoUsaONomeQueAMigrationUsou() {
        servico.criarContaPadrao(USUARIO);

        ArgumentCaptor<Conta> capturada = ArgumentCaptor.forClass(Conta.class);
        verify(repositorioConta).salvar(capturada.capture());
        assertThat(capturada.getValue().getNome()).isEqualTo("Conta principal");
    }

    private void categoriaDeAberturaInexistente() {
        when(repositorioCategoria.buscarPorNomeTipoUsuario(ContaService.CATEGORIA_ABERTURA, TipoTransacao.RECEITA, USUARIO))
            .thenReturn(Optional.empty());
        when(repositorioCategoria.salvar(any(Categoria.class)))
            .thenAnswer(c -> {
                Categoria enviada = c.getArgument(0);
                return new Categoria(CATEGORIA_ABERTURA_ID, enviada.getNome(), enviada.getTipo(), enviada.getUsuarioId());
            });
    }

    private Transacao aberturaGravada() {
        ArgumentCaptor<Transacao> capturada = ArgumentCaptor.forClass(Transacao.class);
        verify(repositorioTransacao).salvar(capturada.capture());
        return capturada.getValue();
    }
}
