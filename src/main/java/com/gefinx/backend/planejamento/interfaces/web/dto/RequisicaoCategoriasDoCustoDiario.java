package com.gefinx.backend.planejamento.interfaces.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * A escolha inteira, e não uma diferença: lista vazia tira o custo diário da meta.
 * Ausente é recusado, para que um corpo malformado não apague a escolha em silêncio.
 */
public record RequisicaoCategoriasDoCustoDiario(
    @NotNull(message = "A lista de categorias é obrigatória")
    Set<@NotNull(message = "O id da categoria é obrigatório") Long> categoriasDoCustoDiario
) {
}
