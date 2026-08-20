package com.financas.backend.usuarios.interfaces.web.dto;

import com.financas.backend.usuarios.dominio.NormalizadorDeEmail;
import com.financas.backend.usuarios.interfaces.web.validacao.SenhaSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequisicaoRegistro(
    @NotBlank(message = "O nome é obrigatório") String nome,
    @NotBlank(message = "O e-mail é obrigatório") @Email(message = "E-mail inválido") String email,
    @NotBlank(message = "A senha é obrigatória") @SenhaSegura String senha
) {

    /**
     * Canoniza o e-mail já na desserialização, antes de a validação correr.
     *
     * <p>O {@code @Email} recusa espaços nas pontas, e normalizar só no caso de uso seria
     * tarde: um endereço colado com espaço ao final morreria como "E-mail inválido" em vez
     * de ser aceito. Quem cola um e-mail arrasta um espaço junto com frequência, e é
     * justamente esse tipo de diferença acidental que esta canonização existe para absorver.
     *
     * <p>O caso de uso normaliza de novo, de propósito: é ele que guarda a invariante, e não
     * pode depender de qual DTO o chamou.
     */
    public RequisicaoRegistro {
        email = NormalizadorDeEmail.normalizar(email);
    }
}
