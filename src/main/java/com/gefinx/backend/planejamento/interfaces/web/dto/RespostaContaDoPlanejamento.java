package com.gefinx.backend.planejamento.interfaces.web.dto;

/** Uma conta do usuário, e se o dinheiro dela conta para o planejamento. */
public record RespostaContaDoPlanejamento(Long id, String nome, boolean entraNoPlanejamento) {
}
