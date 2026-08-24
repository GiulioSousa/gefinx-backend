package com.gefinx.backend.financas.dominio;

/**
 * Onde o dinheiro está: conta corrente, poupança, carteira. A transação pertence a uma
 * conta, e a conta a um usuário.
 *
 * <p>Só nome, deliberadamente. Um tipo de conta seria descritivo e não mudaria cálculo
 * algum — e o único tipo que mudaria, cartão de crédito, tem fatura, limite e saldo de
 * sinal invertido, que é outro domínio.
 *
 * <p>Não guarda saldo inicial: quem abre conta com dinheiro dentro recebe uma transação
 * de abertura, de modo que o saldo continue vindo de uma fonte só — as transações.
 */
public class Conta {

    private final Long id;
    private final String nome;
    private final Long usuarioId;

    public Conta(Long id, String nome, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.usuarioId = usuarioId;
    }

    public static Conta nova(String nome, Long usuarioId) {
        return new Conta(null, nome, usuarioId);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }
}
