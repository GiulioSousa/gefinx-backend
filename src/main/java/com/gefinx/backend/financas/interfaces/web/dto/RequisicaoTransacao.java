package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param valor limitado ao que a coluna {@code NUMERIC(14, 2)} comporta — 12 dígitos
 *              inteiros e 2 casas decimais. Sem esse limite, o banco resolvia o excesso
 *              por conta própria: arredondava a escala em silêncio, gravando um valor
 *              diferente do que a resposta devolvia ao cliente, e estourava em erro
 *              interno quando a parte inteira não cabia. Recusar aqui mantém a regra
 *              visível e devolve `400` no formato do resto da API.
 */
public record RequisicaoTransacao(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
    String descricao,
    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser positivo")
    @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais")
    BigDecimal valor,
    @NotNull(message = "O tipo é obrigatório") TipoTransacao tipo,
    @NotNull(message = "A categoria é obrigatória") Long categoriaId,
    @NotNull(message = "A data é obrigatória") LocalDate dataTransacao
) {
}
