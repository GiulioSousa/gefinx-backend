package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.Recorrencia;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 *
 * <p>{@code recorrencia} ausente ou nula é a despesa que não se repete. {@code parcela} e
 * {@code totalDeParcelas} vêm juntos ou não vêm: "2 de 6" é a segunda de seis, e a série acaba
 * na sexta. O que depende dos dois — e da recorrência — é conferido no domínio.
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
    LocalDate prazo,

    Recorrencia recorrencia,

    @Min(value = 1, message = "A parcela deve ser no mínimo 1")
    @Max(value = 999, message = "A parcela deve ser no máximo 999")
    Integer parcela,

    @Min(value = 1, message = "O total de parcelas deve ser no mínimo 1")
    @Max(value = 999, message = "O total de parcelas deve ser no máximo 999")
    Integer totalDeParcelas
) {
}
