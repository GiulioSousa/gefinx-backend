package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;
import java.time.YearMonth;

/** O que entrou e saiu num mês, sem as transferências: dinheiro que só troca de conta. */
public record MovimentoDoMes(YearMonth mes, BigDecimal receitas, BigDecimal despesas) {

    public static MovimentoDoMes vazio(YearMonth mes) {
        return new MovimentoDoMes(mes, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
