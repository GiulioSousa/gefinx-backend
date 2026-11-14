package com.gefinx.backend.planejamento.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Uma linha de {@code despesas_planejadas_etiquetas}. Leva o {@code usuario_id} junto da
 * etiqueta porque as duas chaves compostas da V19 o pedem: é o que impede, no banco, a despesa
 * de se ligar a uma etiqueta de outra pessoa.
 */
@Embeddable
public class EtiquetaDaDespesaJpaEmbeddable {

    @Column(name = "etiqueta_id", nullable = false)
    private Long etiquetaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected EtiquetaDaDespesaJpaEmbeddable() {
    }

    EtiquetaDaDespesaJpaEmbeddable(Long etiquetaId, Long usuarioId) {
        this.etiquetaId = etiquetaId;
        this.usuarioId = usuarioId;
    }

    Long getEtiquetaId() {
        return etiquetaId;
    }

    /** O Hibernate compara os elementos do conjunto para saber quais linhas apagar e quais inserir. */
    @Override
    public boolean equals(Object outro) {
        return outro instanceof EtiquetaDaDespesaJpaEmbeddable ligacao
            && Objects.equals(etiquetaId, ligacao.etiquetaId)
            && Objects.equals(usuarioId, ligacao.usuarioId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(etiquetaId, usuarioId);
    }
}
