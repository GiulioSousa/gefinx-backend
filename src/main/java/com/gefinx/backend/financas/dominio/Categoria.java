package com.gefinx.backend.financas.dominio;

public class Categoria {

    private final Long id;
    private final String nome;
    private final TipoTransacao tipo;
    private final Long usuarioId;

    public Categoria(Long id, String nome, TipoTransacao tipo, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.usuarioId = usuarioId;
    }

    public static Categoria nova(String nome, TipoTransacao tipo, Long usuarioId) {
        return new Categoria(null, nome, tipo, usuarioId);
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }
}
