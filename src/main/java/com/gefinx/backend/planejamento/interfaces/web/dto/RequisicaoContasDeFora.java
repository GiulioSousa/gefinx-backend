package com.gefinx.backend.planejamento.interfaces.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * A escolha inteira, e não uma diferença: lista vazia põe todas as contas no planejamento.
 * Ausente é recusado, para que um corpo malformado não apague a escolha em silêncio.
 */
public record RequisicaoContasDeFora(
    @NotNull(message = "A lista de contas é obrigatória")
    Set<@NotNull(message = "O id da conta é obrigatório") Long> contasDeFora
) {
}
