package com.financas.backend.usuarios.dominio;

import java.time.LocalDateTime;

public class Usuario {

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final LocalDateTime criadoEm;

    public Usuario(Long id, String nome, String email, String senhaHash, LocalDateTime criadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public static Usuario novo(String nome, String email, String senhaHash) {
        return new Usuario(null, nome, email, senhaHash, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
