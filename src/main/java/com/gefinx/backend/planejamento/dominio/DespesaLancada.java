package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;

/** Uma despesa já lançada em finanças, no que o planejamento precisa saber dela. */
public record DespesaLancada(Long transacaoId, BigDecimal valor, Long contaId) {
}
