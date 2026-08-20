package com.financas.backend.usuarios.dominio;

import java.time.LocalDateTime;

public class Usuario {

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final LocalDateTime criadoEm;

    /**
     * Instante a partir do qual as sessões deste usuário valem.
     *
     * <p>Um token emitido antes dele é recusado mesmo com assinatura e prazo corretos. É o
     * que permite encerrar sessões: sem essa marca, um token entregue não pode mais ser
     * recolhido, e o "Sair" da tela apaga apenas a cópia local.
     */
    private final LocalDateTime sessoesValidasApos;

    public Usuario(Long id, String nome, String email, String senhaHash, LocalDateTime criadoEm,
                   LocalDateTime sessoesValidasApos) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
        this.sessoesValidasApos = sessoesValidasApos;
    }

    public static Usuario novo(String nome, String email, String senhaHash) {
        LocalDateTime agora = LocalDateTime.now();
        return new Usuario(null, nome, email, senhaHash, agora, agora);
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

    public LocalDateTime getSessoesValidasApos() {
        return sessoesValidasApos;
    }
}
