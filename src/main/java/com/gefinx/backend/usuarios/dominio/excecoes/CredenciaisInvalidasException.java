package com.gefinx.backend.usuarios.dominio.excecoes;

/**
 * A mensagem é a mesma para conta inexistente e senha errada, de propósito (Etapa 5):
 * distinguir os dois casos diria a quem sonda quais contas existem. A trilha de auditoria
 * distingue, porque quem a lê é quem opera o sistema.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos");
    }
}
