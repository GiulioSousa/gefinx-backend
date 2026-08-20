package com.financas.backend.usuarios.interfaces.web.dto;

import com.financas.backend.usuarios.dominio.NormalizadorDeEmail;
import com.financas.backend.usuarios.interfaces.web.validacao.SenhaSegura;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param nome  limitado ao que a coluna comporta — `VARCHAR(120)`
 * @param email limitado a 120, <b>abaixo</b> dos 180 da coluna: é um teto de produto, não
 *              o espelho do schema. Nenhum endereço em uso real chega perto disso, e o
 *              menor dos dois é o que vale — não há motivo para aceitar na borda o que só
 *              existiria como abuso. Não "corrigir" para 180 achando que é divergência.
 */
public record RequisicaoRegistro(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    String nome,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 120, message = "O e-mail deve ter no máximo 120 caracteres")
    String email,

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
