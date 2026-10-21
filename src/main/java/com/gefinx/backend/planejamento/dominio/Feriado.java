package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;

/** Um feriado nacional: o dia, e o nome com que ele aparece na tela. */
public record Feriado(LocalDate data, String nome) {
}
