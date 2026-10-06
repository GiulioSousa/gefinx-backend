package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A transação que pagou uma despesa planejada, no que o histórico mostra dela. Os nomes vêm
 * prontos de finanças, e não os ids: o planejamento não tem onde resolvê-los.
 */
public record PagamentoLancado(Long transacaoId, BigDecimal valor, LocalDate data, String conta, String categoria) {
}
