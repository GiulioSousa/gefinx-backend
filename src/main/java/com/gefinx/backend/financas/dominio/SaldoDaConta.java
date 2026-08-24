package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;

public record SaldoDaConta(Long contaId, BigDecimal totalReceitas, BigDecimal totalDespesas) {

    public BigDecimal saldo() {
        return totalReceitas.subtract(totalDespesas);
    }
}
