package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RespostaTransacao(
    Long id,
    String descricao,
    BigDecimal valor,
    TipoTransacao tipo,
    LocalDate dataTransacao,
    Long categoriaId,
    String nomeCategoria,
    Long contaId,
    String nomeConta
) {

    public static RespostaTransacao apartirDoDominio(Transacao transacao, String nomeCategoria, String nomeConta) {
        return new RespostaTransacao(
            transacao.getId(),
            transacao.getDescricao(),
            transacao.getValor(),
            transacao.getTipo(),
            transacao.getDataTransacao(),
            transacao.getCategoriaId(),
            nomeCategoria,
            transacao.getContaId(),
            nomeConta
        );
    }
}
