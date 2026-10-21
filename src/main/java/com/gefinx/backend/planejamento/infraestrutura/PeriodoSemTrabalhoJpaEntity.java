package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** {@code criado_em} fica sem mapeamento, pelo motivo registrado em {@code ContaJpaEntity}. */
@Entity
@Table(name = "periodos_sem_trabalho")
public class PeriodoSemTrabalhoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private LocalDate inicio;

    @Column(nullable = false)
    private LocalDate fim;

    private String descricao;

    protected PeriodoSemTrabalhoJpaEntity() {
    }

    public static PeriodoSemTrabalhoJpaEntity apartirDoDominio(Long usuarioId, PeriodoSemTrabalho periodo) {
        PeriodoSemTrabalhoJpaEntity entidade = new PeriodoSemTrabalhoJpaEntity();
        entidade.id = periodo.id();
        entidade.usuarioId = usuarioId;
        entidade.inicio = periodo.inicio();
        entidade.fim = periodo.fim();
        entidade.descricao = periodo.descricao();
        return entidade;
    }

    public PeriodoSemTrabalho paraDominio() {
        return new PeriodoSemTrabalho(id, inicio, fim, descricao);
    }
}
