package com.financas.backend.financas.aplicacao;

import com.financas.backend.financas.dominio.RepositorioCategoria;
import com.financas.backend.financas.dominio.RepositorioTransacao;
import com.financas.backend.financas.dominio.TipoTransacao;
import com.financas.backend.financas.dominio.Transacao;
import com.financas.backend.financas.dominio.Categoria;
import com.financas.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.financas.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransacaoService {

    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioCategoria repositorioCategoria;

    public TransacaoService(RepositorioTransacao repositorioTransacao, RepositorioCategoria repositorioCategoria) {
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioCategoria = repositorioCategoria;
    }

    public List<Transacao> listar(Long usuarioId) {
        return repositorioTransacao.listarPorUsuario(usuarioId);
    }

    public Transacao criar(
        Long usuarioId,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long categoriaId,
        LocalDate dataTransacao
    ) {
        validarCategoria(usuarioId, categoriaId, tipo);
        Transacao transacao = Transacao.nova(descricao, valor, tipo, dataTransacao, categoriaId, usuarioId);
        return repositorioTransacao.salvar(transacao);
    }

    public Transacao atualizar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long categoriaId,
        LocalDate dataTransacao
    ) {
        Transacao transacaoExistente = buscarOuLancar(usuarioId, id);
        validarCategoria(usuarioId, categoriaId, tipo);
        Transacao transacaoAtualizada = new Transacao(
            id, descricao, valor, tipo, dataTransacao, categoriaId, usuarioId, transacaoExistente.getCriadoEm()
        );
        return repositorioTransacao.salvar(transacaoAtualizada);
    }

    public void excluir(Long usuarioId, Long id) {
        buscarOuLancar(usuarioId, id);
        repositorioTransacao.excluir(id);
    }

    private Transacao buscarOuLancar(Long usuarioId, Long id) {
        return repositorioTransacao.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));
    }

    /**
     * A categoria é quem define se o lançamento entra ou sai. Aceitar uma transação
     * cujo tipo diverge do dela produz um saldo que nenhum relatório por categoria
     * consegue explicar — e recusar é melhor do que corrigir em silêncio, pelo mesmo
     * motivo que o valor fora de escala é recusado em vez de arredondado.
     */
    private void validarCategoria(Long usuarioId, Long categoriaId, TipoTransacao tipo) {
        Categoria categoria = repositorioCategoria.buscarPorIdEUsuario(categoriaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));

        if (categoria.getTipo() != tipo) {
            throw new TipoIncompativelComCategoriaException(tipo, categoria.getNome(), categoria.getTipo());
        }
    }
}
