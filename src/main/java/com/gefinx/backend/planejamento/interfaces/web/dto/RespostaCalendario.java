package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.aplicacao.CalendarioService.CalendarioDoAno;

import java.time.LocalDate;
import java.util.List;

/** Os feriados nacionais do ano, cada um dizendo se foi ignorado, e os períodos que tocam o ano. */
public record RespostaCalendario(int ano, List<FeriadoDoAno> feriados, List<RespostaPeriodoSemTrabalho> periodos) {

    public record FeriadoDoAno(LocalDate data, String nome, boolean ignorado) {
    }

    public static RespostaCalendario apartirDoDominio(CalendarioDoAno calendario) {
        return new RespostaCalendario(
            calendario.ano(),
            calendario.feriados().stream()
                .map(item -> new FeriadoDoAno(item.feriado().data(), item.feriado().nome(), item.ignorado()))
                .toList(),
            calendario.periodos().stream().map(RespostaPeriodoSemTrabalho::apartirDoDominio).toList()
        );
    }
}
