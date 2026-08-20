package com.financas.backend.financas.interfaces.web.dto;

import com.financas.backend.financas.dominio.TipoTransacao;
import com.financas.backend.financas.dominio.Transacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaTransacao(
    Long id,
    String descricao,
    BigDecimal valor,
    TipoTransacao tipo,
    LocalDate dataTransacao,
    Long categoriaId,
    String nomeCategoria
) {

    public static RespostaTransacao apartirDoDominio(Transacao transacao, String nomeCategoria) {
        return new RespostaTransacao(
            transacao.getId(),
            transacao.getDescricao(),
            transacao.getValor(),
            transacao.getTipo(),
            transacao.getDataTransacao(),
            transacao.getCategoriaId(),
            nomeCategoria
        );
    }
}
