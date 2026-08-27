package com.gefinx.backend.financas.dominio;

/**
 * Enum compartilhado entre {@link Categoria} e {@link Transacao}, de propósito: a Etapa 10
 * amarra o tipo da transação ao da sua categoria comparando os dois lados, e essa
 * comparação exige que sejam o mesmo tipo Java.
 *
 * <p>{@code TRANSFERENCIA} vale só para transação. Categoria nunca o assume — a coluna
 * {@code categorias.tipo} segue em {@code VARCHAR(10)} e nem comporta a palavra, e a
 * borda recusa antes com mensagem própria.
 */
public enum TipoTransacao {
    RECEITA,
    DESPESA,
    TRANSFERENCIA
}
