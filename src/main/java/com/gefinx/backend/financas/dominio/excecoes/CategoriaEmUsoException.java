package com.gefinx.backend.financas.dominio.excecoes;

public class CategoriaEmUsoException extends RuntimeException {

    public CategoriaEmUsoException() {
        super("Não é possível excluir a categoria pois existem transações vinculadas a ela");
    }
}
