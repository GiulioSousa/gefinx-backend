package com.gefinx.backend.financas.dominio;

import java.time.LocalDate;

/**
 * Os recortes possíveis da listagem de transações. Todo campo é opcional, e {@code null}
 * significa "não filtra por isso" — os que vierem preenchidos se somam.
 *
 * <p>As datas são inclusivas nas duas pontas: quem pede o mês de agosto informa o dia 1º e
 * o dia 31 e espera os dois dentro do resultado.
 */
public record FiltroDeTransacoes(
    LocalDate dataInicio,
    LocalDate dataFim,
    TipoTransacao tipo,
    Long contaId,
    Long categoriaId
) {

    public static final FiltroDeTransacoes VAZIO = new FiltroDeTransacoes(null, null, null, null, null);
}
