package com.gefinx.backend.planejamento.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Lê só as pendentes. A paga saiu do planejamento, e a tela não a mostra; para desfazer o
 * pagamento, exclui-se a transação, e a despesa volta a ser pendente por conta própria.
 */
public interface RepositorioDespesaPlanejada {

    DespesaPlanejada salvar(DespesaPlanejada despesa);

    /** As pendentes, por prazo — a ordem em que o plano consome o saldo. */
    List<DespesaPlanejada> listarPendentesPorUsuario(Long usuarioId);

    Optional<DespesaPlanejada> buscarPendentePorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    /** Das transações informadas, as que pagam alguma despesa planejada do usuário. */
    Set<Long> filtrarPagamentos(Long usuarioId, Collection<Long> transacaoIds);
}
