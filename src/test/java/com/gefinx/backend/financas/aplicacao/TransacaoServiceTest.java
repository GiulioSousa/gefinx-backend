package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long CATEGORIA = 10L;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 20);

    private RepositorioTransacao repositorioTransacao;
    private RepositorioCategoria repositorioCategoria;
    private TransacaoService servico;

    @BeforeEach
    void preparar() {
        repositorioTransacao = mock(RepositorioTransacao.class);
        repositorioCategoria = mock(RepositorioCategoria.class);
        servico = new TransacaoService(repositorioTransacao, repositorioCategoria);
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
            USUARIO, 5L, "descricao", BigDecimal.TEN, TipoTransacao.DESPESA, CATEGORIA, DATA
        )).isInstanceOf(TipoIncompativelComCategoriaException.class);
    }

    @Test
    void continuaRecusandoCategoriaDeOutroUsuario() {
        when(repositorioCategoria.buscarPorIdEUsuario(CATEGORIA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criar(TipoTransacao.DESPESA))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private void criar(TipoTransacao tipo) {
        servico.criar(USUARIO, "descricao", BigDecimal.TEN, tipo, CATEGORIA, DATA);
    }

    private void categoriaExistente(TipoTransacao tipo, String nome) {
        when(repositorioCategoria.buscarPorIdEUsuario(CATEGORIA, USUARIO))
            .thenReturn(Optional.of(new Categoria(CATEGORIA, nome, tipo, USUARIO)));
    }

    private Transacao transacaoExistente() {
        return Transacao.nova("antiga", BigDecimal.ONE, TipoTransacao.RECEITA, DATA, CATEGORIA, USUARIO);
    }
}
