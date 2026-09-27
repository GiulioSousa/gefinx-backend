package com.gefinx.backend.planejamento.interfaces.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os limites de {@code descricao} e {@code valor} são os de {@code RequisicaoTransacao}, e
 * pelo mesmo motivo que a coluna repete o tipo: paga, a despesa vira transação, e o que
 * coubesse aqui e não lá travaria o pagamento.
 */
public record RequisicaoDespesaPlanejada(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
    String descricao,

    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser positivo")
    @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais")
    BigDecimal valor,

    @NotNull(message = "O prazo é obrigatório")
    LocalDate prazo
) {
}
