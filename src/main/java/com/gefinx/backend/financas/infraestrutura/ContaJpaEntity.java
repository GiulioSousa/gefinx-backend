package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.Conta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A coluna {@code criado_em} existe na tabela e não é mapeada aqui de propósito: nada no
 * domínio a lê, e o {@code DEFAULT now()} a preenche. Mapeá-la só para ignorá-la depois
 * daria a impressão de que alguém depende dela.
 */
@Entity
@Table(name = "contas")
public class ContaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected ContaJpaEntity() {
    }

    public ContaJpaEntity(Long id, String nome, Long usuarioId) {
        this.id = id;
        this.nome = nome;
        this.usuarioId = usuarioId;
    }

    public static ContaJpaEntity apartirDoDominio(Conta conta) {
        return new ContaJpaEntity(conta.getId(), conta.getNome(), conta.getUsuarioId());
    }

    public Conta paraDominio() {
        return new Conta(id, nome, usuarioId);
    }

    public Long getId() {
        return id;
    }
}
