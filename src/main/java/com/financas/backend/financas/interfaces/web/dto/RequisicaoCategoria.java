package com.financas.backend.financas.interfaces.web.dto;

import com.financas.backend.financas.dominio.TipoTransacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequisicaoCategoria(
    @NotBlank(message = "O nome é obrigatório") String nome,
    @NotNull(message = "O tipo é obrigatório") TipoTransacao tipo
) {
}
