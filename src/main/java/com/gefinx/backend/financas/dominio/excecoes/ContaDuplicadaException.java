package com.gefinx.backend.financas.dominio.excecoes;

public class ContaDuplicadaException extends RuntimeException {

    public ContaDuplicadaException(String nome) {
        super("Já existe uma conta com o nome: " + nome);
    }
}
