package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.aplicacao.RelatorioService.ResumoDoMes;

import java.math.BigDecimal;

/** {@code mes} como {@code 2026-10}; {@code saldoAoFim} é o consolidado no último dia do mês. */
public record RespostaMesDoRelatorio(String mes, BigDecimal receitas, BigDecimal despesas, BigDecimal resultado, BigDecimal saldoAoFim) {

    public static RespostaMesDoRelatorio apartirDoResumo(ResumoDoMes resumo) {
        return new RespostaMesDoRelatorio(
            resumo.mes().toString(), resumo.receitas(), resumo.despesas(), resumo.resultado(), resumo.saldoAoFim()
        );
    }
}
