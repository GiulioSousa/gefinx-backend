package com.financas.backend.financas.infraestrutura;

import com.financas.backend.financas.dominio.TipoTransacao;
import com.financas.backend.financas.dominio.Transacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacoes")
public class TransacaoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Column(name = "data_transacao", nullable = false)
    private LocalDate dataTransacao;

    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected TransacaoJpaEntity() {
    }

    public TransacaoJpaEntity(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        LocalDate dataTransacao,
        Long categoriaId,
        Long usuarioId,
        LocalDateTime criadoEm
    ) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.tipo = tipo;
        this.dataTransacao = dataTransacao;
        this.categoriaId = categoriaId;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }

    public static TransacaoJpaEntity apartirDoDominio(Transacao transacao) {
        return new TransacaoJpaEntity(
            transacao.getId(),
            transacao.getDescricao(),
            transacao.getValor(),
            transacao.getTipo(),
            transacao.getDataTransacao(),
            transacao.getCategoriaId(),
            transacao.getUsuarioId(),
            transacao.getCriadoEm()
        );
    }

    public Transacao paraDominio() {
        return new Transacao(id, descricao, valor, tipo, dataTransacao, categoriaId, usuarioId, criadoEm);
    }

    public Long getId() {
        return id;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }
}
