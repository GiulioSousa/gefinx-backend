package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Dias em que não se trabalha além dos domingos e feriados nacionais: um feriado da cidade,
 * uma folga, as férias. Um dia só é o período de início e fim iguais. Os dois extremos
 * contam.
 *
 * <p>A descrição é opcional; sem ela, o período aparece como "Dia sem trabalho".
 */
public record PeriodoSemTrabalho(Long id, LocalDate inicio, LocalDate fim, String descricao) {

    /** Um ano, contando o bissexto: férias cabem com folga, e um erro de digitação de ano não. */
    public static final int DIAS_NO_MAXIMO = 366;

    private static final String NOME_PADRAO = "Dia sem trabalho";

    public PeriodoSemTrabalho {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O fim do período vem antes do início");
        }
    }

    public boolean contem(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(fim);
    }

    public boolean sobrepoe(PeriodoSemTrabalho outro) {
        return !outro.fim.isBefore(inicio) && !outro.inicio.isAfter(fim);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public String nome() {
        return descricao == null ? NOME_PADRAO : descricao;
    }
}
