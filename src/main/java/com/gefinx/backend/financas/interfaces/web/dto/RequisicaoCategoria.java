package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param nome limitado ao que a coluna comporta — `VARCHAR(80)`. Sem esse teto, o excesso
 *             chegava ao banco e a violação voltava como erro interno em vez de `400`.
 */
public record RequisicaoCategoria(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
    String nome,

    @NotNull(message = "O tipo é obrigatório") TipoTransacao tipo
) {
}
