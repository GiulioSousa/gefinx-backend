package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.Pagina;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    public Pagina<Transacao> listar(Long usuarioId, FiltroDeTransacoes filtro, int pagina, int tamanho) {
        return repositorioTransacao.listarPorUsuario(usuarioId, filtro, pagina, tamanho);
    }

    public Transacao criar(
        Long usuarioId,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long categoriaId,
        Long contaId,
        Long contaDestinoId,
        LocalDate dataTransacao
    ) {
        validarReferencias(usuarioId, tipo, categoriaId, contaId, contaDestinoId);

        Transacao transacao = tipo == TipoTransacao.TRANSFERENCIA
            ? Transacao.novaTransferencia(descricao, valor, dataTransacao, contaId, contaDestinoId, usuarioId)
            : Transacao.nova(descricao, valor, tipo, dataTransacao, categoriaId, contaId, usuarioId);

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
        Long contaDestinoId,
        LocalDate dataTransacao
    ) {
        Transacao existente = buscarOuLancar(usuarioId, id);
        validarReferencias(usuarioId, tipo, categoriaId, contaId, contaDestinoId);

        boolean ehTransferencia = tipo == TipoTransacao.TRANSFERENCIA;
        // Zerar o campo do outro caso, em vez de repassar o que veio, é o que garante que
        // uma transação editada de transferência para despesa (ou o contrário) não fique
        // com resquício do formato anterior. As CHECKs da V8 recusariam a linha híbrida,
        // mas com erro de banco em vez de um resultado correto.
        Transacao atualizada = new Transacao(
            id,
            descricao,
            valor,
            tipo,
            dataTransacao,
            ehTransferencia ? null : categoriaId,
            contaId,
            ehTransferencia ? contaDestinoId : null,
            usuarioId,
            existente.getCriadoEm()
        );

        return repositorioTransacao.salvar(atualizada);
    }

    public void excluir(Long usuarioId, Long id) {
        buscarOuLancar(usuarioId, id);
        repositorioTransacao.excluir(id);
    }

    /**
     * Valida o que só o banco sabe responder: se a categoria e as contas existem e
     * pertencem a quem está pedindo.
     *
     * <p>A coerência entre {@code tipo}, {@code categoriaId} e {@code contaDestinoId} não
     * é checada aqui — ela já veio verificada da borda, por {@code @TransferenciaValida},
     * que é onde a checagem pura cabe e onde a mensagem sai no campo que falhou.
     */
    private void validarReferencias(
        Long usuarioId,
        TipoTransacao tipo,
        Long categoriaId,
        Long contaId,
        Long contaDestinoId
    ) {
        validarConta(usuarioId, contaId);

        if (tipo == TipoTransacao.TRANSFERENCIA) {
            validarConta(usuarioId, contaDestinoId);
        } else {
            validarCategoria(usuarioId, categoriaId, tipo);
        }
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
     * forma, pelas chaves estrangeiras compostas da V7 e da V8 — mas a mensagem de lá
     * seria um erro interno, e esta é a resposta que o cliente deve receber.
     */
    private void validarConta(Long usuarioId, Long contaId) {
        repositorioConta.buscarPorIdEUsuario(contaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }
}
