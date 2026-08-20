package com.financas.backend.financas.interfaces.web.dto;

import com.financas.backend.financas.dominio.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RequisicaoTransacao(
    @NotBlank(message = "A descrição é obrigatória") String descricao,
    @NotNull(message = "O valor é obrigatório") @Positive(message = "O valor deve ser positivo") BigDecimal valor,
    @NotNull(message = "O tipo é obrigatório") TipoTransacao tipo,
    @NotNull(message = "A categoria é obrigatória") Long categoriaId,
    @NotNull(message = "A data é obrigatória") LocalDate dataTransacao
) {
}
