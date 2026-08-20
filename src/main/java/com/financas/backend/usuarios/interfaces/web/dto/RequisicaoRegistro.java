package com.financas.backend.usuarios.interfaces.web.dto;

import com.financas.backend.usuarios.interfaces.web.validacao.SenhaSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequisicaoRegistro(
    @NotBlank(message = "O nome é obrigatório") String nome,
    @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
    @NotBlank(message = "A senha é obrigatória") @SenhaSegura String senha
) {
}
