package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaDespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo) {

    public static RespostaDespesaPlanejada apartirDoDominio(DespesaPlanejada despesa) {
        return new RespostaDespesaPlanejada(despesa.getId(), despesa.getDescricao(), despesa.getValor(), despesa.getPrazo());
    }
}
