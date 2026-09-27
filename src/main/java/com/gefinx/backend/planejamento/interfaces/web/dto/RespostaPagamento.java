package com.gefinx.backend.planejamento.interfaces.web.dto;

/** O id da transação criada — a prova do pagamento, e o que se exclui para desfazê-lo. */
public record RespostaPagamento(Long transacaoId) {
}
