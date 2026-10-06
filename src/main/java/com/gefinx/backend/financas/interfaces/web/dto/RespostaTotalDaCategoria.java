package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.aplicacao.RelatorioService.TotalDaCategoria;

import java.math.BigDecimal;

public record RespostaTotalDaCategoria(Long categoriaId, String nome, BigDecimal total) {

    public static RespostaTotalDaCategoria apartirDoTotal(TotalDaCategoria total) {
        return new RespostaTotalDaCategoria(total.categoriaId(), total.nome(), total.total());
    }
}
