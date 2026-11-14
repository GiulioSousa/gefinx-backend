package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.Recorrencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code parcela} e {@code totalDeParcelas} vêm nulos na despesa que não é parcelada; {@code etiquetaIds}, vazia na que não tem etiqueta. */
public record RespostaDespesaPlanejada(
    Long id,
    String descricao,
    BigDecimal valor,
    LocalDate prazo,
    Recorrencia recorrencia,
    Integer parcela,
    Integer totalDeParcelas,
    List<Long> etiquetaIds
) {

    public static RespostaDespesaPlanejada apartirDoDominio(DespesaPlanejada despesa) {
        return new RespostaDespesaPlanejada(
            despesa.getId(), despesa.getDescricao(), despesa.getValor(), despesa.getPrazo(), despesa.getRecorrencia(),
            despesa.getParcela(), despesa.getTotalDeParcelas(), despesa.getEtiquetaIds()
        );
    }
}
