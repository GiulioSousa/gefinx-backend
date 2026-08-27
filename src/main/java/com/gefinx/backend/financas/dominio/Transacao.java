package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Transacao {

    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final TipoTransacao tipo;
    private final LocalDate dataTransacao;
    private final Long categoriaId;
    private final Long contaId;
    private final Long contaDestinoId;
    private final Long usuarioId;
    private final LocalDateTime criadoEm;

    public Transacao(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        LocalDate dataTransacao,
        Long categoriaId,
        Long contaId,
        Long contaDestinoId,
        Long usuarioId,
        LocalDateTime criadoEm
    ) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.tipo = tipo;
        this.dataTransacao = dataTransacao;
        this.categoriaId = categoriaId;
        this.contaId = contaId;
        this.contaDestinoId = contaDestinoId;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }

    /** Receita ou despesa: tem categoria, não tem conta de destino. */
    public static Transacao nova(
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        LocalDate dataTransacao,
        Long categoriaId,
        Long contaId,
        Long usuarioId
    ) {
        return new Transacao(
            null, descricao, valor, tipo, dataTransacao, categoriaId, contaId, null, usuarioId,
            LocalDateTime.now()
        );
    }

    /**
     * Transferência: o oposto exato: sem categoria, com conta de destino. Os dois campos
     * são fixados aqui em vez de virem por parâmetro porque a combinação é a definição do
     * que é uma transferência, e o banco a exige assim — as CHECKs da V8 recusariam
     * qualquer outra.
     */
    public static Transacao novaTransferencia(
        String descricao,
        BigDecimal valor,
        LocalDate dataTransacao,
        Long contaOrigemId,
        Long contaDestinoId,
        Long usuarioId
    ) {
        return new Transacao(
            null, descricao, valor, TipoTransacao.TRANSFERENCIA, dataTransacao, null,
            contaOrigemId, contaDestinoId, usuarioId, LocalDateTime.now()
        );
    }

    public boolean ehTransferencia() {
        return tipo == TipoTransacao.TRANSFERENCIA;
    }

    public Long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public LocalDate getDataTransacao() {
        return dataTransacao;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public Long getContaId() {
        return contaId;
    }

    public Long getContaDestinoId() {
        return contaDestinoId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
