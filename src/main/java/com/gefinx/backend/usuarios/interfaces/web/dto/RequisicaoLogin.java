package com.gefinx.backend.usuarios.interfaces.web.dto;

import com.gefinx.backend.usuarios.dominio.NormalizadorDeEmail;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequisicaoLogin(
    @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
    @NotBlank(message = "A senha é obrigatória") String senha
) {

    /** Mesma canonização do cadastro: ver {@link RequisicaoRegistro}. */
    public RequisicaoLogin {
        email = NormalizadorDeEmail.normalizar(email);
    }
}
