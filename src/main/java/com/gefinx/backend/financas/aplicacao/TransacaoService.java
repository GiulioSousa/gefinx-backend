package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.Pagina;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.financas.dominio.excecoes.SaldoNegativoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

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

    @Transactional
    public TransacaoComNomes criar(
        Long usuarioId,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        Long categoriaId,
        Long contaId,
        Long contaDestinoId,
        LocalDate dataTransacao
    ) {
        Referencias referencias = validarReferencias(usuarioId, tipo, categoriaId, contaId, contaDestinoId);
        Set<Long> contas = contasEnvolvidas(contaId, contaDestinoId);
        repositorioConta.travar(contas);

        Transacao transacao = tipo == TipoTransacao.TRANSFERENCIA
            ? Transacao.novaTransferencia(descricao, valor, dataTransacao, contaId, contaDestinoId, usuarioId)
            : Transacao.nova(descricao, valor, tipo, dataTransacao, categoriaId, contaId, usuarioId);

        Transacao salva = repositorioTransacao.salvar(transacao);
        conferirSaldos(usuarioId, contas);
        return comNomes(salva, referencias);
    }

    @Transactional
    public TransacaoComNomes atualizar(
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
        Referencias referencias = validarReferencias(usuarioId, tipo, categoriaId, contaId, contaDestinoId);
        // As de antes e as de depois: trocar a conta, ou o destino de uma transferência, tira
        // dinheiro de onde ele estava, e a conta que perde é justamente a que pode estourar.
        Set<Long> contas = contasEnvolvidas(
            existente.getContaId(), existente.getContaDestinoId(), contaId, contaDestinoId
        );
        repositorioConta.travar(contas);

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

        Transacao salva = repositorioTransacao.salvar(atualizada);
        conferirSaldos(usuarioId, contas);
        return comNomes(salva, referencias);
    }

    /** Excluir uma receita, ou uma transferência recebida, também tira dinheiro da conta. */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Transacao existente = buscarOuLancar(usuarioId, id);
        Set<Long> contas = contasEnvolvidas(existente.getContaId(), existente.getContaDestinoId());
        repositorioConta.travar(contas);

        repositorioTransacao.excluir(id);
        conferirSaldos(usuarioId, contas);
    }

    /**
     * <b>Nenhuma conta pode ter saldo negativo em dia algum da sua história</b> — regra do
     * dono do projeto (Etapa 26). Vale o saldo no fim de cada dia, e para todos os dias, não
     * só para hoje: uma despesa lançada com data antiga é recusada se a conta, naquele dia,
     * não tinha o dinheiro, ainda que tenha hoje. O consolidado não precisa de conferência
     * própria — é a soma das contas, e soma de valores nunca negativos não fica negativa.
     *
     * <p>Confere depois de gravar, e não antes, por simulação: o banco já sabe somar o que a
     * transação mudou, e refazer essa conta em memória para cada combinação de conta, data e
     * tipo seria uma segunda implementação do saldo — que poderia discordar da primeira. A
     * recusa é uma exceção dentro da transação, que desfaz a gravação.
     */
    private void conferirSaldos(Long usuarioId, Set<Long> contaIds) {
        for (Long contaId : contaIds) {
            repositorioTransacao.primeiroDiaNegativo(contaId).ifPresent(estouro -> {
                String nome = validarConta(usuarioId, contaId).getNome();
                throw new SaldoNegativoException(nome, estouro.dia(), estouro.saldo());
            });
        }
    }

    /** Sem nulos e em ordem: a ordem é a das travas, e a das mensagens de recusa. */
    private static Set<Long> contasEnvolvidas(Long... contaIds) {
        Set<Long> contas = new TreeSet<>();
        for (Long contaId : contaIds) {
            if (contaId != null) {
                contas.add(contaId);
            }
        }
        return contas;
    }

    /**
     * Valida o que só o banco sabe responder: se a categoria e as contas existem e
     * pertencem a quem está pedindo.
     *
     * <p>A coerência entre {@code tipo}, {@code categoriaId} e {@code contaDestinoId} não
     * é checada aqui — ela já veio verificada da borda, por {@code @TransferenciaValida},
     * que é onde a checagem pura cabe e onde a mensagem sai no campo que falhou.
     */
    private Referencias validarReferencias(
        Long usuarioId,
        TipoTransacao tipo,
        Long categoriaId,
        Long contaId,
        Long contaDestinoId
    ) {
        Conta conta = validarConta(usuarioId, contaId);

        if (tipo == TipoTransacao.TRANSFERENCIA) {
            return new Referencias(null, conta, validarConta(usuarioId, contaDestinoId));
        }
        return new Referencias(validarCategoria(usuarioId, categoriaId, tipo), conta, null);
    }

    private TransacaoComNomes comNomes(Transacao transacao, Referencias referencias) {
        return new TransacaoComNomes(
            transacao,
            referencias.categoria() == null ? null : referencias.categoria().getNome(),
            referencias.conta().getNome(),
            referencias.contaDestino() == null ? null : referencias.contaDestino().getNome()
        );
    }

    /** O que a validacao carregou, guardado para a borda nao precisar buscar de novo. */
    private record Referencias(Categoria categoria, Conta conta, Conta contaDestino) {
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
    private Categoria validarCategoria(Long usuarioId, Long categoriaId, TipoTransacao tipo) {
        Categoria categoria = repositorioCategoria.buscarPorIdEUsuario(categoriaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));

        if (categoria.getTipo() != tipo) {
            throw new TipoIncompativelComCategoriaException(tipo, categoria.getNome(), categoria.getTipo());
        }

        return categoria;
    }

    /**
     * Busca pela dupla (id, usuário) e não só pelo id: uma conta de outro dono devolve
     * "não encontrada", em vez de confirmar que ela existe. O banco recusaria de qualquer
     * forma, pelas chaves estrangeiras compostas da V7 e da V8 — mas a mensagem de lá
     * seria um erro interno, e esta é a resposta que o cliente deve receber.
     */
    private Conta validarConta(Long usuarioId, Long contaId) {
        return repositorioConta.buscarPorIdEUsuario(contaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }
}
