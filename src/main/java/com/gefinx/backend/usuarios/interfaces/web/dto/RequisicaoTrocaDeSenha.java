package com.gefinx.backend.usuarios.interfaces.web.dto;

import com.gefinx.backend.usuarios.interfaces.web.validacao.SenhaSegura;
import jakarta.validation.constraints.NotBlank;

/**
 * A senha atual não passa pela política: ela já é a senha da conta, e uma criada antes da
 * política, ou pelo script de quem opera, precisa continuar servindo para trocá-la. A política
 * vale para a nova.
 */
public record RequisicaoTrocaDeSenha(
    @NotBlank(message = "A senha atual é obrigatória") String senhaAtual,

    @NotBlank(message = "A nova senha é obrigatória") @SenhaSegura String novaSenha
) {
}
