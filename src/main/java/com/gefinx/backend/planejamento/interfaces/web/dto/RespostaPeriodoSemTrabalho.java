package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;

import java.time.LocalDate;

public record RespostaPeriodoSemTrabalho(Long id, LocalDate inicio, LocalDate fim, String descricao) {

    public static RespostaPeriodoSemTrabalho apartirDoDominio(PeriodoSemTrabalho periodo) {
        return new RespostaPeriodoSemTrabalho(periodo.id(), periodo.inicio(), periodo.fim(), periodo.descricao());
    }
}
