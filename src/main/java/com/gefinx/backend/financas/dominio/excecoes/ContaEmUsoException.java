package com.gefinx.backend.financas.dominio.excecoes;

public class ContaEmUsoException extends RuntimeException {

    public ContaEmUsoException() {
        super("Não é possível excluir a conta pois existem transações vinculadas a ela");
    }
}
