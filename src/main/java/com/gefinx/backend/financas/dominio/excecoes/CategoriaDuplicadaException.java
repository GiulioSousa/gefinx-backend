package com.gefinx.backend.financas.dominio.excecoes;

public class CategoriaDuplicadaException extends RuntimeException {

    public CategoriaDuplicadaException(String nome) {
        super("Já existe uma categoria com o nome: " + nome);
    }
}
