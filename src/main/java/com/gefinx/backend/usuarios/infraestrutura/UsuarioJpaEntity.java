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

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "sessoes_validas_apos", nullable = false)
    private LocalDateTime sessoesValidasApos;

    protected UsuarioJpaEntity() {
    }

    public UsuarioJpaEntity(Long id, String nome, String email, String senhaHash, LocalDateTime criadoEm,
                            LocalDateTime sessoesValidasApos) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
        this.sessoesValidasApos = sessoesValidasApos;
    }

    public static UsuarioJpaEntity apartirDoDominio(Usuario usuario) {
        return new UsuarioJpaEntity(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail(),
            usuario.getSenhaHash(),
            usuario.getCriadoEm(),
            usuario.getSessoesValidasApos()
        );
    }

    public Usuario paraDominio() {
        return new Usuario(id, nome, email, senhaHash, criadoEm, sessoesValidasApos);
    }

    public Long getId() {
        return id;
    }
}
