package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Os pares categoria/conta-de-destino são mutuamente exclusivos e vêm nulos conforme o
 * tipo: transferência não tem categoria, receita e despesa não têm destino.
 */
public record RespostaTransacao(
    Long id,
    String descricao,
    BigDecimal valor,
    TipoTransacao tipo,
    LocalDate dataTransacao,
    Long categoriaId,
    String nomeCategoria,
    Long contaId,
    String nomeConta,
    Long contaDestinoId,
    String nomeContaDestino
) {

    public static RespostaTransacao apartirDoDominio(
        Transacao transacao,
        String nomeCategoria,
        String nomeConta,
        String nomeContaDestino
    ) {
        return new RespostaTransacao(
            transacao.getId(),
            transacao.getDescricao(),
            transacao.getValor(),
            transacao.getTipo(),
            transacao.getDataTransacao(),
            transacao.getCategoriaId(),
            nomeCategoria,
            transacao.getContaId(),
            nomeConta,
            transacao.getContaDestinoId(),
            nomeContaDestino
        );
    }
}
