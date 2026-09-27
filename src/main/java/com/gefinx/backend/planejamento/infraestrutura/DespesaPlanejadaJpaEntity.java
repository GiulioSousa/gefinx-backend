package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code criado_em} fica sem mapeamento, pelo motivo registrado em {@code ContaJpaEntity}. */
@Entity
@Table(name = "despesas_planejadas")
public class DespesaPlanejadaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate prazo;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "transacao_id")
    private Long transacaoId;

    protected DespesaPlanejadaJpaEntity() {
    }

    private DespesaPlanejadaJpaEntity(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId, Long transacaoId) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.prazo = prazo;
        this.usuarioId = usuarioId;
        this.transacaoId = transacaoId;
    }

    public static DespesaPlanejadaJpaEntity apartirDoDominio(DespesaPlanejada despesa) {
        return new DespesaPlanejadaJpaEntity(
            despesa.getId(), despesa.getDescricao(), despesa.getValor(), despesa.getPrazo(), despesa.getUsuarioId(),
            despesa.getTransacaoId()
        );
    }

    public DespesaPlanejada paraDominio() {
        return new DespesaPlanejada(id, descricao, valor, prazo, usuarioId, transacaoId);
    }
}
