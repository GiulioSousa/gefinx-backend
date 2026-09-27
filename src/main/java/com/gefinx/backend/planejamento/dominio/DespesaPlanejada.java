package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um pagamento que ainda vai acontecer: quanto e até quando. Não é transação — não mexe no
 * saldo. Vira transação quando é paga, e aí sai do planejamento, para que o saldo continue
 * vindo de uma fonte só.
 *
 * <p>Paga é a que tem {@code transacaoId}: a transação é a prova do pagamento. Excluí-la
 * desfaz a ligação no banco, e a despesa volta ao planejamento.
 */
public class DespesaPlanejada {

    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final LocalDate prazo;
    private final Long usuarioId;
    private final Long transacaoId;

    public DespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId) {
        this(id, descricao, valor, prazo, usuarioId, null);
    }

    public DespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId, Long transacaoId) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.prazo = prazo;
        this.usuarioId = usuarioId;
        this.transacaoId = transacaoId;
    }

    public static DespesaPlanejada nova(String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId) {
        return new DespesaPlanejada(null, descricao, valor, prazo, usuarioId);
    }

    public DespesaPlanejada pagaCom(Long transacaoId) {
        return new DespesaPlanejada(id, descricao, valor, prazo, usuarioId, transacaoId);
    }

    public boolean estaPaga() {
        return transacaoId != null;
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

    public Long getTransacaoId() {
        return transacaoId;
    }
}
