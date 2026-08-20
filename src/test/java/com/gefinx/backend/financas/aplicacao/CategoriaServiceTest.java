package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.excecoes.TipoDaCategoriaEmUsoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoriaServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long CATEGORIA = 10L;

    private RepositorioCategoria repositorioCategoria;
    private RepositorioTransacao repositorioTransacao;
    private CategoriaService servico;

    @BeforeEach
    void preparar() {
        repositorioCategoria = mock(RepositorioCategoria.class);
        repositorioTransacao = mock(RepositorioTransacao.class);
        servico = new CategoriaService(repositorioCategoria, repositorioTransacao);

        when(repositorioCategoria.buscarPorIdEUsuario(CATEGORIA, USUARIO))
            .thenReturn(Optional.of(new Categoria(CATEGORIA, "Alimentação", TipoTransacao.DESPESA, USUARIO)));
        when(repositorioCategoria.salvar(any(Categoria.class))).thenAnswer(c -> c.getArgument(0));
    }

    @Test
    void recusaTrocaDeTipoQuandoACategoriaTemTransacoes() {
        when(repositorioTransacao.existePorCategoria(CATEGORIA)).thenReturn(true);

        assertThatThrownBy(() -> servico.atualizar(USUARIO, CATEGORIA, "Alimentação", TipoTransacao.RECEITA))
            .as("as despesas já lançadas passariam a contar como receita no saldo")
            .isInstanceOf(TipoDaCategoriaEmUsoException.class);
    }

    @Test
    void naoGravaNadaAoRecusarATrocaDeTipo() {
        when(repositorioTransacao.existePorCategoria(CATEGORIA)).thenReturn(true);

        assertThatThrownBy(() -> servico.atualizar(USUARIO, CATEGORIA, "Alimentação", TipoTransacao.RECEITA))
            .isInstanceOf(TipoDaCategoriaEmUsoException.class);

        verify(repositorioCategoria, never()).salvar(any());
    }

    @Test
    void permiteTrocaDeTipoQuandoACategoriaNaoTemTransacoes() {
        when(repositorioTransacao.existePorCategoria(CATEGORIA)).thenReturn(false);
        when(repositorioCategoria.existePorNomeTipoUsuario(anyString(), any(), anyLong())).thenReturn(false);

        assertThatCode(() -> servico.atualizar(USUARIO, CATEGORIA, "Alimentação", TipoTransacao.RECEITA))
            .doesNotThrowAnyException();
    }

    @Test
    void permiteRenomearCategoriaComTransacoes() {
        when(repositorioTransacao.existePorCategoria(CATEGORIA)).thenReturn(true);
        when(repositorioCategoria.existePorNomeTipoUsuario(anyString(), any(), anyLong())).thenReturn(false);

        assertThatCode(() -> servico.atualizar(USUARIO, CATEGORIA, "Alimentação e mercado", TipoTransacao.DESPESA))
            .as("só o tipo fica travado; o nome continua livre")
            .doesNotThrowAnyException();
    }
}
