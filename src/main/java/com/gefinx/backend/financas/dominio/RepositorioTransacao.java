package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepositorioTransacao {

    Transacao salvar(Transacao transacao);

    /**
     * Uma fatia do histórico do usuário, do lançamento mais recente para o mais antigo.
     *
     * <p>Devolve {@link Pagina} e não {@code List} porque o histórico só cresce: uma
     * listagem sem teto carrega tudo que já foi lançado para exibir a primeira tela.
     */
    Pagina<Transacao> listarPorUsuario(Long usuarioId, FiltroDeTransacoes filtro, int pagina, int tamanho);

    Optional<Transacao> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    boolean existePorCategoria(Long categoriaId);

    /** Conta origem ou destino: as duas pontas prendem a conta contra exclusão. */
    boolean existePorConta(Long contaId);

    /*
     * As somas do saldo recebem o período e o dono mesmo quando a conta já foi conferida: são
     * o mesmo recorte da listagem, montado pelos mesmos predicados, de modo que o total de um
     * período e a lista desse período não têm como discordar sobre quais linhas entram.
     */

    BigDecimal somarValorPorUsuarioETipo(Long usuarioId, TipoTransacao tipo, Periodo periodo);

    BigDecimal somarValorPorContaETipo(Long usuarioId, Long contaId, TipoTransacao tipo, Periodo periodo);

    /** Assinado: o que entrou na conta por transferência menos o que saiu dela. */
    BigDecimal somarTransferenciasLiquidasDaConta(Long usuarioId, Long contaId, Periodo periodo);

    /** Saldo de cada conta do usuário numa consulta só, em vez de uma por conta. */
    List<SaldoDaConta> resumirSaldoPorConta(Long usuarioId);
}
