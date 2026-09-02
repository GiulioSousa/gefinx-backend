package com.gefinx.backend.usuarios.dominio;

import java.time.LocalDateTime;

public class Usuario {

    private final Long id;

    /**
     * Nome de usuário, na forma canônica — é o que identifica a conta no login.
     *
     * <p>Substituiu o e-mail na Etapa em que o cadastro pela interface deixou de existir.
     * O endereço nunca foi verificado nem usado para enviar coisa alguma, então não
     * identificava melhor do que este campo e ainda guardava um dado pessoal a mais.
     */
    private final String usuario;

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

    public Usuario(Long id, String usuario, String senhaHash, LocalDateTime criadoEm,
                   LocalDateTime sessoesValidasApos) {
        this.id = id;
        this.usuario = usuario;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
        this.sessoesValidasApos = sessoesValidasApos;
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
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
