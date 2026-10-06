package com.gefinx.backend.planejamento.dominio;

import java.util.Optional;

/**
 * Uma despesa do histórico: o que se planejou, junto do que de fato se pagou. Os dois valores
 * ficam lado a lado porque costumam divergir — paga-se um pouco a mais ou a menos, e em outro
 * dia —, e a diferença é justamente o que o histórico tem a contar.
 *
 * <p>O pagamento é opcional por segurança, não por regra: excluir a transação devolve a
 * despesa às pendentes ({@code ON DELETE SET NULL}), mas entre ler a despesa e ler a
 * transação alguém pode tê-la excluído. Nesse caso a despesa aparece sem os dados do
 * pagamento, em vez de sumir da página e desacertar a contagem.
 */
public record DespesaPaga(DespesaPlanejada despesa, Optional<PagamentoLancado> pagamento) {

    /** Pagar depois do prazo é o único atraso que o histórico registra; o dia do prazo ainda vale. */
    public boolean pagaComAtraso() {
        return pagamento.map(lancado -> lancado.data().isAfter(despesa.getPrazo())).orElse(false);
    }
}
