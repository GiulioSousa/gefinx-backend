package com.gefinx.backend.usuarios.dominio.excecoes;

/**
 * Uma troca de senha recusada por causa de um dos campos: a senha atual que não confere, ou a
 * nova igual à atual. Carrega o campo para a resposta apontá-lo, como as falhas de validação.
 */
public class SenhaRecusadaException extends RuntimeException {

    private final String campo;

    public SenhaRecusadaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
