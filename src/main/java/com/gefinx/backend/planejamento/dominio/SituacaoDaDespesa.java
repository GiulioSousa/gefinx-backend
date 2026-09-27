package com.gefinx.backend.planejamento.dominio;

public enum SituacaoDaDespesa {
    /** O saldo de agora já paga esta despesa e todas as que vencem antes dela. */
    COBERTA,
    /** Falta dinheiro, e ainda há dias de trabalho até o prazo. */
    EM_ANDAMENTO,
    /**
     * Falta dinheiro e não sobra dia de trabalho antes do prazo: vence hoje, ou hoje é
     * domingo e ela vence na segunda.
     */
    SEM_DIAS_DE_TRABALHO,
    /** O prazo passou e ela não foi paga — mesmo que o saldo já a cubra. */
    ATRASADA
}
