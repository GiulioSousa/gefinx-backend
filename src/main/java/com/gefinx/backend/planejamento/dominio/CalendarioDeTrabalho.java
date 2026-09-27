package com.gefinx.backend.planejamento.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/**
 * Quais dias rendem ganho. Hoje é de segunda a sábado, sem feriados — ficaram fora da
 * primeira versão, e é aqui que entrariam, sem que quem conta os dias precise saber.
 */
public final class CalendarioDeTrabalho {

    public static final CalendarioDeTrabalho SEGUNDA_A_SABADO = new CalendarioDeTrabalho(EnumSet.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
    ));

    private final Set<DayOfWeek> diasDaSemana;

    private CalendarioDeTrabalho(Set<DayOfWeek> diasDaSemana) {
        this.diasDaSemana = EnumSet.copyOf(diasDaSemana);
    }

    /**
     * Dias de trabalho de {@code inicio}, inclusive, até {@code fim}, exclusive. O fim fica
     * de fora porque é o prazo: o que se ganha no próprio dia do pagamento pode chegar
     * depois dele. Intervalo vazio ou invertido dá zero.
     *
     * <p>Semanas inteiras são contadas por multiplicação, e só a sobra dia a dia — um prazo
     * a anos de distância não custa um laço de milhares de voltas.
     */
    public int contarDias(LocalDate inicio, LocalDate fim) {
        long total = ChronoUnit.DAYS.between(inicio, fim);
        if (total <= 0) {
            return 0;
        }

        long semanas = total / 7;
        long dias = semanas * diasDaSemana.size();
        for (LocalDate dia = inicio.plusWeeks(semanas); dia.isBefore(fim); dia = dia.plusDays(1)) {
            if (diasDaSemana.contains(dia.getDayOfWeek())) {
                dias++;
            }
        }
        return Math.toIntExact(dias);
    }
}
