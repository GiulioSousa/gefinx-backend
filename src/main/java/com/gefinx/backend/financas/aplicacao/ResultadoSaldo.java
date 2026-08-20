package com.gefinx.backend.financas.aplicacao;

import java.math.BigDecimal;

public record ResultadoSaldo(BigDecimal totalReceitas, BigDecimal totalDespesas, BigDecimal saldo) {
}
