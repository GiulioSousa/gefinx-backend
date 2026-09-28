package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O saldo de uma conta no fim de um dia: tudo o que entrou e saiu até ele, inclusive. */
public record SaldoNoDia(LocalDate dia, BigDecimal saldo) {
}
