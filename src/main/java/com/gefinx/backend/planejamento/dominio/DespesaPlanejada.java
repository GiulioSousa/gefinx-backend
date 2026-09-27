package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um pagamento que ainda vai acontecer: quanto e até quando. Não é transação — não mexe no
 * saldo. Vira transação quando é paga, e aí sai do planejamento, para que o saldo continue
 * vindo de uma fonte só.
 */
public class DespesaPlanejada {

    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final LocalDate prazo;
    private final Long usuarioId;

    public DespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.prazo = prazo;
        this.usuarioId = usuarioId;
    }

    public static DespesaPlanejada nova(String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId) {
        return new DespesaPlanejada(null, descricao, valor, prazo, usuarioId);
    }

    public Long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }
}
