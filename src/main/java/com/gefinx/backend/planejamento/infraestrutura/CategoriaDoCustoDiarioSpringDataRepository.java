package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoriaDoCustoDiarioSpringDataRepository extends JpaRepository<CategoriaDoCustoDiarioJpaEntity, Long> {

    List<CategoriaDoCustoDiarioJpaEntity> findByUsuarioId(Long usuarioId);

    /** Um DELETE só, pelos mesmos motivos de {@link ContaDeForaSpringDataRepository#apagarDoUsuario}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CategoriaDoCustoDiarioJpaEntity c WHERE c.usuarioId = :usuarioId")
    void apagarDoUsuario(Long usuarioId);
}
