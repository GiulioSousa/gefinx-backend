package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
public class CategoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected CategoriaJpaEntity() {
    }

    public CategoriaJpaEntity(Long id, String nome, TipoTransacao tipo, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.usuarioId = usuarioId;
    }

    public static CategoriaJpaEntity apartirDoDominio(Categoria categoria) {
        return new CategoriaJpaEntity(categoria.getId(), categoria.getNome(), categoria.getTipo(), categoria.getUsuarioId());
    }

    public Categoria paraDominio() {
        return new Categoria(id, nome, tipo, usuarioId);
    }

    public Long getId() {
        return id;
    }
}
