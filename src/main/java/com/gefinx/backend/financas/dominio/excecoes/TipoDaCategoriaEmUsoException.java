package com.gefinx.backend.financas.dominio.excecoes;

/**
 * Trocar o tipo de uma categoria que já tem lançamentos inverteria o sentido deles no
 * saldo — uma despesa passaria a contar como receita. Renomear continua permitido; é só
 * o tipo que fica travado enquanto houver transações.
 */
public class TipoDaCategoriaEmUsoException extends RuntimeException {

    public TipoDaCategoriaEmUsoException() {
        super("Não é possível mudar o tipo de uma categoria que já possui transações");
    }
}
