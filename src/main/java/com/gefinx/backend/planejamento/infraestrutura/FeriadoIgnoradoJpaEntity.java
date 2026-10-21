package com.gefinx.backend.planejamento.infraestrutura;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Só lida: a gravação é um INSERT com ON CONFLICT, em {@link FeriadoIgnoradoSpringDataRepository}. */
@Entity
@Table(name = "feriados_ignorados")
public class FeriadoIgnoradoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private LocalDate data;

    protected FeriadoIgnoradoJpaEntity() {
    }

    public LocalDate getData() {
        return data;
    }
}
