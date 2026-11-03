package com.gefinx.backend.planejamento.dominio;

import java.util.Objects;

/**
 * Uma marca para a despesa planejada: de que cartão é a compra, por exemplo. Só visual — não
 * muda valor, prazo nem a meta. Cada despesa tem no máximo uma.
 */
public record Etiqueta(Long id, String nome, CorDaEtiqueta cor) {

    /** Cabe num selo do cartão no celular sem quebrar a linha. */
    public static final int NOME_NO_MAXIMO = 30;

    public Etiqueta {
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(cor, "cor");
    }
}
