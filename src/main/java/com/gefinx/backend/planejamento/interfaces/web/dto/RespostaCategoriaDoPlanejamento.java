package com.gefinx.backend.planejamento.interfaces.web.dto;

/** Uma categoria de despesa do usuário, e se o gasto dela entra no custo diário da meta. */
public record RespostaCategoriaDoPlanejamento(Long id, String nome, boolean entraNoCustoDiario) {
}
