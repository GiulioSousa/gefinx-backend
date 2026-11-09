package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma receita ou despesa já lançada em finanças, no que o planejamento precisa saber dela.
 * Transferência não chega aqui: não é ganho nem gasto, e não tem categoria.
 */
public record Lancamento(Long transacaoId, BigDecimal valor, LocalDate data, Long contaId, Long categoriaId) {
}
