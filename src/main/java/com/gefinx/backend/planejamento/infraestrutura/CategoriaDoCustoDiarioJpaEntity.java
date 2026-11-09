package com.gefinx.backend.planejamento.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias_do_custo_diario")
public class CategoriaDoCustoDiarioJpaEntity {

    @Id
    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected CategoriaDoCustoDiarioJpaEntity() {
    }

    public CategoriaDoCustoDiarioJpaEntity(Long categoriaId, Long usuarioId) {
        this.categoriaId = categoriaId;
        this.usuarioId = usuarioId;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }
}
