package com.gefinx.backend.planejamento.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Quais dias rendem ganho: de segunda a sábado, menos os feriados nacionais e os períodos
 * sem trabalho de quem usa. Um feriado pode ser ignorado — o dia em que se decidiu trabalhar
 * mesmo assim volta a contar.
 *
 * <p>Quem conta os dias não precisa saber de nada disso: o plano só pergunta quantos há
 * até cada prazo.
 */
public final class CalendarioDeTrabalho {

    private static final Locale PORTUGUES = Locale.of("pt", "BR");

    /**
     * Só os dias da semana, sem feriado nenhum. É a base dos outros, e o que os testes do plano
     * usam para que a conta deles não mude com o ano.
     */
    public static final CalendarioDeTrabalho SEGUNDA_A_SABADO = new CalendarioDeTrabalho(
        EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), false, Set.of(), List.of()
    );

    private final Set<DayOfWeek> diasDaSemana;
    private final boolean comFeriadosNacionais;
    private final Set<LocalDate> feriadosIgnorados;
    private final List<PeriodoSemTrabalho> periodos;

    private CalendarioDeTrabalho(
        Set<DayOfWeek> diasDaSemana,
        boolean comFeriadosNacionais,
        Set<LocalDate> feriadosIgnorados,
        List<PeriodoSemTrabalho> periodos
    ) {
        this.diasDaSemana = EnumSet.copyOf(diasDaSemana);
        this.comFeriadosNacionais = comFeriadosNacionais;
        this.feriadosIgnorados = Set.copyOf(feriadosIgnorados);
        this.periodos = List.copyOf(periodos);
    }

    /** O calendário de quem usa: de segunda a sábado, sem os feriados que não ignorou e sem os períodos. */
    public static CalendarioDeTrabalho comFeriadosNacionais(
        Set<LocalDate> feriadosIgnorados,
        List<PeriodoSemTrabalho> periodos
    ) {
        return new CalendarioDeTrabalho(SEGUNDA_A_SABADO.diasDaSemana, true, feriadosIgnorados, periodos);
    }

    /**
     * Dias de trabalho de {@code inicio}, inclusive, até {@code fim}, exclusive. O fim fica
     * de fora porque é o prazo: o que se ganha no próprio dia do pagamento pode chegar
     * depois dele. Intervalo vazio ou invertido dá zero.
     *
     * <p>Semanas inteiras são contadas por multiplicação, e só a sobra dia a dia — um prazo
     * a anos de distância não custa um laço de milhares de voltas. As folgas são poucas, e
     * descontadas depois, uma a uma.
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
        return Math.toIntExact(dias - folgasEmDiaDeSemana(inicio, fim));
    }

    /**
     * Por que o dia não conta, ou vazio se ele conta. O feriado vem antes do período, e o
     * período antes do domingo: é o nome mais útil para quem lê.
     */
    public Optional<String> motivoDaFolga(LocalDate dia) {
        if (comFeriadosNacionais && !feriadosIgnorados.contains(dia)) {
            Optional<Feriado> feriado = FeriadosNacionais.em(dia);
            if (feriado.isPresent()) {
                return Optional.of(feriado.get().nome());
            }
        }
        for (PeriodoSemTrabalho periodo : periodos) {
            if (periodo.contem(dia)) {
                return Optional.of(periodo.nome());
            }
        }
        if (!diasDaSemana.contains(dia.getDayOfWeek())) {
            String nome = dia.getDayOfWeek().getDisplayName(TextStyle.FULL, PORTUGUES);
            return Optional.of(Character.toUpperCase(nome.charAt(0)) + nome.substring(1));
        }
        return Optional.empty();
    }

    /**
     * Feriados e dias de período que cairiam num dia de trabalho, sem contar duas vezes o
     * feriado dentro das férias. Os que caem num domingo já ficaram de fora na contagem.
     */
    private long folgasEmDiaDeSemana(LocalDate inicio, LocalDate fim) {
        Set<LocalDate> folgas = new HashSet<>();

        if (comFeriadosNacionais) {
            for (int ano = inicio.getYear(); ano <= fim.minusDays(1).getYear(); ano++) {
                for (Feriado feriado : FeriadosNacionais.doAno(ano)) {
                    LocalDate data = feriado.data();
                    if (!data.isBefore(inicio) && data.isBefore(fim) && !feriadosIgnorados.contains(data)) {
                        folgas.add(data);
                    }
                }
            }
        }

        for (PeriodoSemTrabalho periodo : periodos) {
            LocalDate de = periodo.inicio().isAfter(inicio) ? periodo.inicio() : inicio;
            LocalDate ate = periodo.fim().plusDays(1).isBefore(fim) ? periodo.fim().plusDays(1) : fim;
            for (LocalDate dia = de; dia.isBefore(ate); dia = dia.plusDays(1)) {
                folgas.add(dia);
            }
        }

        return folgas.stream().filter(dia -> diasDaSemana.contains(dia.getDayOfWeek())).count();
    }
}
