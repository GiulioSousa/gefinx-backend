package com.gefinx.backend.planejamento.interfaces.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Sem {@code fim}, é um dia só. A descrição é opcional, e em branco vale como ausente. */
public record RequisicaoPeriodoSemTrabalho(
    @NotNull(message = "O início é obrigatório")
    LocalDate inicio,
    LocalDate fim,
    @Size(max = 100, message = "A descrição deve ter no máximo 100 caracteres")
    String descricao
) {
}
