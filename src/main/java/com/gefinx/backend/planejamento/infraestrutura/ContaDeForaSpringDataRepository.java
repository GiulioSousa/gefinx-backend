package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContaDeForaSpringDataRepository extends JpaRepository<ContaDeForaJpaEntity, Long> {

    List<ContaDeForaJpaEntity> findByUsuarioId(Long usuarioId);

    /**
     * Um DELETE só, e não o {@code deleteBy} derivado, que carrega cada linha para apagá-la
     * uma a uma. O {@code flushAutomatically} manda antes o que estiver pendente, e o
     * {@code clearAutomatically} tira do contexto as entidades que o DELETE apagou por fora:
     * sem isso, regravar uma conta que continua de fora esbarraria na cópia velha em memória.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContaDeForaJpaEntity c WHERE c.usuarioId = :usuarioId")
    void apagarDoUsuario(Long usuarioId);
}
