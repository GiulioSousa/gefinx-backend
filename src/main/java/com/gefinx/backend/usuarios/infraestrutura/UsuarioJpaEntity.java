package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.usuarios.dominio.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class UsuarioJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String usuario;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "sessoes_validas_apos", nullable = false)
    private LocalDateTime sessoesValidasApos;

    protected UsuarioJpaEntity() {
    }

    public UsuarioJpaEntity(Long id, String usuario, String senhaHash, LocalDateTime criadoEm,
                            LocalDateTime sessoesValidasApos) {
        this.id = id;
        this.usuario = usuario;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
        this.sessoesValidasApos = sessoesValidasApos;
    }

    public Usuario paraDominio() {
        return new Usuario(id, usuario, senhaHash, criadoEm, sessoesValidasApos);
    }

    public Long getId() {
        return id;
    }
}
