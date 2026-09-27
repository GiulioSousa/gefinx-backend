package com.gefinx.backend.planejamento.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contas_fora_do_planejamento")
public class ContaDeForaJpaEntity {

    @Id
    @Column(name = "conta_id")
    private Long contaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected ContaDeForaJpaEntity() {
    }

    public ContaDeForaJpaEntity(Long contaId, Long usuarioId) {
        this.contaId = contaId;
        this.usuarioId = usuarioId;
    }

    public Long getContaId() {
        return contaId;
    }
}
