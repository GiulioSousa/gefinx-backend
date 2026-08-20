package com.financas.backend.financas.dominio;

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
    private final Long usuarioId;
    private final LocalDateTime criadoEm;

    public Transacao(
        Long id,
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        LocalDate dataTransacao,
        Long categoriaId,
        Long usuarioId,
        LocalDateTime criadoEm
    ) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.tipo = tipo;
        this.dataTransacao = dataTransacao;
        this.categoriaId = categoriaId;
        this.usuarioId = usuarioId;
        this.criadoEm = criadoEm;
    }

    public static Transacao nova(
        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,
        LocalDate dataTransacao,
        Long categoriaId,
        Long usuarioId
    ) {
        return new Transacao(null, descricao, valor, tipo, dataTransacao, categoriaId, usuarioId, LocalDateTime.now());
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

    public Long getUsuarioId() {
        return usuarioId;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
