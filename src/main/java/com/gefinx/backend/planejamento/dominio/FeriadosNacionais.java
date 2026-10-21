package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Os feriados nacionais de qualquer ano, calculados, e não guardados: não há lista a manter
 * nem ano em que ela acabe. Só os de lei federal — Carnaval e Corpus Christi são ponto
 * facultativo, e feriado estadual ou municipal depende de onde se mora; quem para nesses dias
 * os cadastra como período sem trabalho.
 *
 * <p>A Paixão de Cristo é o único que se move: a sexta-feira antes da Páscoa.
 */
public final class FeriadosNacionais {

    /** A Lei 14.759/2023 fez de 20 de novembro feriado nacional; antes, só onde a lei local dizia. */
    private static final int PRIMEIRO_ANO_DA_CONSCIENCIA_NEGRA = 2024;

    private FeriadosNacionais() {
    }

    /** Em ordem de data. */
    public static List<Feriado> doAno(int ano) {
        List<Feriado> feriados = new ArrayList<>(List.of(
            new Feriado(LocalDate.of(ano, 1, 1), "Confraternização Universal"),
            new Feriado(pascoa(ano).minusDays(2), "Paixão de Cristo"),
            new Feriado(LocalDate.of(ano, 4, 21), "Tiradentes"),
            new Feriado(LocalDate.of(ano, 5, 1), "Dia do Trabalho"),
            new Feriado(LocalDate.of(ano, 9, 7), "Independência do Brasil"),
            new Feriado(LocalDate.of(ano, 10, 12), "Nossa Senhora Aparecida"),
            new Feriado(LocalDate.of(ano, 11, 2), "Finados"),
            new Feriado(LocalDate.of(ano, 11, 15), "Proclamação da República"),
            new Feriado(LocalDate.of(ano, 12, 25), "Natal")
        ));
        if (ano >= PRIMEIRO_ANO_DA_CONSCIENCIA_NEGRA) {
            feriados.add(new Feriado(LocalDate.of(ano, 11, 20), "Consciência Negra"));
        }
        feriados.sort(Comparator.comparing(Feriado::data));
        return List.copyOf(feriados);
    }

    public static Optional<Feriado> em(LocalDate dia) {
        return doAno(dia.getYear()).stream()
            .filter(feriado -> feriado.data().equals(dia))
            .findFirst();
    }

    /**
     * O domingo de Páscoa no calendário gregoriano, pelo algoritmo de Meeus/Jones/Butcher. Os
     * nomes de uma letra são os do algoritmo publicado, para que a conferência com ele seja
     * linha a linha.
     */
    static LocalDate pascoa(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia);
    }
}
