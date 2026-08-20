package com.financas.backend.financas.aplicacao;

import com.financas.backend.financas.dominio.Categoria;
import com.financas.backend.financas.dominio.RepositorioCategoria;
import com.financas.backend.financas.dominio.RepositorioTransacao;
import com.financas.backend.financas.dominio.TipoTransacao;
import com.financas.backend.financas.dominio.excecoes.CategoriaDuplicadaException;
import com.financas.backend.financas.dominio.excecoes.CategoriaEmUsoException;
import com.financas.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.financas.backend.financas.dominio.excecoes.TipoDaCategoriaEmUsoException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CategoriaService {

    private static final Map<TipoTransacao, List<String>> CATEGORIAS_PADRAO = Map.of(
        TipoTransacao.RECEITA, List.of("Salário", "Outras Receitas"),
        TipoTransacao.DESPESA, List.of("Alimentação", "Transporte", "Moradia", "Lazer", "Saúde", "Outros")
    );

    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioTransacao repositorioTransacao;

    public CategoriaService(RepositorioCategoria repositorioCategoria, RepositorioTransacao repositorioTransacao) {
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioTransacao = repositorioTransacao;
    }

    public List<Categoria> listar(Long usuarioId) {
        return repositorioCategoria.listarPorUsuario(usuarioId);
    }

    public Categoria buscarPorId(Long usuarioId, Long id) {
        return buscarOuLancar(usuarioId, id);
    }

    public Categoria criar(Long usuarioId, String nome, TipoTransacao tipo) {
        validarNaoDuplicada(usuarioId, nome, tipo);
        return repositorioCategoria.salvar(Categoria.nova(nome, tipo, usuarioId));
    }

    /**
     * Renomear é sempre permitido; o tipo fica travado enquanto a categoria tiver
     * transações. Trocá-lo inverteria o sentido dos lançamentos já feitos — uma despesa
     * passaria a contar como receita no saldo, sem que ninguém tivesse pedido isso.
     */
    public Categoria atualizar(Long usuarioId, Long id, String nome, TipoTransacao tipo) {
        Categoria categoriaExistente = buscarOuLancar(usuarioId, id);
        if (categoriaExistente.getTipo() != tipo && repositorioTransacao.existePorCategoria(id)) {
            throw new TipoDaCategoriaEmUsoException();
        }

        if (!categoriaExistente.getNome().equalsIgnoreCase(nome) || categoriaExistente.getTipo() != tipo) {
            validarNaoDuplicada(usuarioId, nome, tipo);
        }
        Categoria categoriaAtualizada = new Categoria(id, nome, tipo, usuarioId);
        return repositorioCategoria.salvar(categoriaAtualizada);
    }

    public void excluir(Long usuarioId, Long id) {
        Categoria categoria = buscarOuLancar(usuarioId, id);
        if (repositorioTransacao.existePorCategoria(categoria.getId())) {
            throw new CategoriaEmUsoException();
        }
        repositorioCategoria.excluir(id);
    }

    public void criarCategoriasPadrao(Long usuarioId) {
        CATEGORIAS_PADRAO.forEach((tipo, nomes) ->
            nomes.forEach(nome -> repositorioCategoria.salvar(Categoria.nova(nome, tipo, usuarioId)))
        );
    }

    private Categoria buscarOuLancar(Long usuarioId, Long id) {
        return repositorioCategoria.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private void validarNaoDuplicada(Long usuarioId, String nome, TipoTransacao tipo) {
        if (repositorioCategoria.existePorNomeTipoUsuario(nome, tipo, usuarioId)) {
            throw new CategoriaDuplicadaException(nome);
        }
    }
}
