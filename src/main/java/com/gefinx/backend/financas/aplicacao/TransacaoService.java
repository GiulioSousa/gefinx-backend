package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransacaoService {

    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioConta repositorioConta;

    public TransacaoService(
        RepositorioTransacao repositorioTransacao,
        RepositorioCategoria repositorioCategoria,
        RepositorioConta repositorioConta
    ) {
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioConta = repositorioConta;
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
        Long contaId,
        LocalDate dataTransacao
    ) {
        validarCategoria(usuarioId, categoriaId, tipo);
        validarConta(usuarioId, contaId);
        Transacao transacao = Transacao.nova(descricao, valor, tipo, dataTransacao, categoriaId, contaId, usuarioId);
        return repositorioTransacao.salvar(transacao);
    }

    public Transacao atualizar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long categoriaId,
        Long contaId,
        LocalDate dataTransacao
    ) {
        Transacao transacaoExistente = buscarOuLancar(usuarioId, id);
        validarCategoria(usuarioId, categoriaId, tipo);
        validarConta(usuarioId, contaId);
        Transacao transacaoAtualizada = new Transacao(
            id, descricao, valor, tipo, dataTransacao, categoriaId, contaId, usuarioId,
            transacaoExistente.getCriadoEm()
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

    /**
     * Busca pela dupla (id, usuário) e não só pelo id: uma conta de outro dono devolve
     * "não encontrada", em vez de confirmar que ela existe. O banco recusaria de qualquer
     * forma, pela chave estrangeira composta da V7 — mas a mensagem de lá seria um erro
     * interno, e esta é a resposta que o cliente deve receber.
     */
    private void validarConta(Long usuarioId, Long contaId) {
        repositorioConta.buscarPorIdEUsuario(contaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }
}
