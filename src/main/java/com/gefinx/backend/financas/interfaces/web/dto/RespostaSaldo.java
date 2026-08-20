package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.aplicacao.ResultadoSaldo;

import java.math.BigDecimal;

public record RespostaSaldo(BigDecimal totalReceitas, BigDecimal totalDespesas, BigDecimal saldo) {

    public static RespostaSaldo apartirDoResultado(ResultadoSaldo resultado) {
        return new RespostaSaldo(resultado.totalReceitas(), resultado.totalDespesas(), resultado.saldo());
    }
}
