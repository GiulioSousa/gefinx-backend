package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.usuarios.dominio.Usuario;
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

    protected UsuarioJpaEntity() {
    }

    public UsuarioJpaEntity(Long id, String nome, String email, String senhaHash, LocalDateTime criadoEm) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = criadoEm;
    }

    public static UsuarioJpaEntity apartirDoDominio(Usuario usuario) {
        return new UsuarioJpaEntity(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail(),
            usuario.getSenhaHash(),
            usuario.getCriadoEm()
        );
    }

    public Usuario paraDominio() {
        return new Usuario(id, nome, email, senhaHash, criadoEm);
    }

    public Long getId() {
        return id;
    }
}
