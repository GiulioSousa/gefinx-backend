package com.gefinx.backend.usuarios.interfaces.web.dto;

import com.gefinx.backend.usuarios.dominio.NormalizadorDeUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param usuario limitado a 60, o mesmo teto da coluna. Não é enfeite: este valor vira a
 *                chave do balde da trava por conta e entra na trilha de auditoria, e um
 *                campo sem teto deixaria as duas coisas crescerem ao gosto de quem chama.
 */
public record RequisicaoLogin(
    @NotBlank(message = "O usuário é obrigatório")
    @Size(max = 60, message = "O usuário deve ter no máximo 60 caracteres")
    String usuario,

    @NotBlank(message = "A senha é obrigatória") String senha
) {

    /**
     * Canoniza o nome já na desserialização, antes de a validação correr, para que um nome
     * colado com espaço ao final não morra como "obrigatório" tendo conteúdo.
     *
     * <p>O caso de uso normaliza de novo, de propósito: é ele que guarda a invariante, e
     * não pode depender de qual DTO o chamou.
     */
    public RequisicaoLogin {
        usuario = NormalizadorDeUsuario.normalizar(usuario);
    }
}
