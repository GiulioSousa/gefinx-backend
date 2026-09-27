package com.gefinx.backend.planejamento.interfaces.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A transação que paga a despesa: os campos de {@code RequisicaoTransacao} com o tipo fixo
 * em despesa, e os mesmos limites — é uma transação comum que vai ser criada.
 */
public record RequisicaoPagamento(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
    String descricao,

    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser positivo")
    @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais")
    BigDecimal valor,

    @NotNull(message = "A data é obrigatória")
    LocalDate dataTransacao,

    @NotNull(message = "A categoria é obrigatória")
    Long categoriaId,

    @NotNull(message = "A conta é obrigatória")
    Long contaId
) {
}
