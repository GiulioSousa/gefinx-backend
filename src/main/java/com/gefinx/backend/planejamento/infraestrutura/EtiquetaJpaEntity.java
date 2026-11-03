package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.CorDaEtiqueta;
import com.gefinx.backend.planejamento.dominio.Etiqueta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code criado_em} fica sem mapeamento, pelo motivo registrado em {@code ContaJpaEntity}. */
@Entity
@Table(name = "etiquetas")
public class EtiquetaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorDaEtiqueta cor;

    protected EtiquetaJpaEntity() {
    }

    public static EtiquetaJpaEntity apartirDoDominio(Long usuarioId, Etiqueta etiqueta) {
        EtiquetaJpaEntity entidade = new EtiquetaJpaEntity();
        entidade.id = etiqueta.id();
        entidade.usuarioId = usuarioId;
        entidade.nome = etiqueta.nome();
        entidade.cor = etiqueta.cor();
        return entidade;
    }

    public Etiqueta paraDominio() {
        return new Etiqueta(id, nome, cor);
    }
}
