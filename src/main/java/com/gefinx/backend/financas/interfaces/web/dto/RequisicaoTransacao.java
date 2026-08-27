package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.interfaces.web.validacao.TransferenciaValida;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param valor          limitado ao que a coluna {@code NUMERIC(14, 2)} comporta — 12
 *                       dígitos inteiros e 2 casas decimais. Sem esse limite, o banco
 *                       resolvia o excesso por conta própria: arredondava a escala em
 *                       silêncio, gravando um valor diferente do que a resposta devolvia
 *                       ao cliente, e estourava em erro interno quando a parte inteira
 *                       não cabia. Recusar aqui mantém a regra visível e devolve `400`
 *                       no formato do resto da API.
 * @param categoriaId    obrigatório em receita e despesa, <b>proibido</b> em
 *                       transferência. Sem {@code @NotNull} porque a obrigatoriedade é
 *                       condicional — quem decide é {@code @TransferenciaValida}, que
 *                       enxerga o {@code tipo} junto.
 * @param contaId        obrigatório desde a Etapa 19. Não há conta implícita: escolher
 *                       uma pelo cliente quando o campo faltasse acertaria enquanto o
 *                       usuário tivesse uma conta só, e passaria a lançar dinheiro no
 *                       lugar errado assim que ele tivesse duas.
 * @param contaDestinoId o inverso de {@code categoriaId}: obrigatório em transferência,
 *                       proibido no resto.
 */
@TransferenciaValida
public record RequisicaoTransacao(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
    String descricao,
    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser positivo")
    @Digits(integer = 12, fraction = 2, message = "O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais")
    BigDecimal valor,
    @NotNull(message = "O tipo é obrigatório") TipoTransacao tipo,
    Long categoriaId,
    @NotNull(message = "A conta é obrigatória") Long contaId,
    Long contaDestinoId,
    @NotNull(message = "A data é obrigatória") LocalDate dataTransacao
) {
}
