package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface FeriadoIgnoradoSpringDataRepository extends JpaRepository<FeriadoIgnoradoJpaEntity, Long> {

    List<FeriadoIgnoradoJpaEntity> findByUsuarioId(Long usuarioId);

    /**
     * SQL nativo pelo ON CONFLICT: conferir antes e gravar depois deixaria dois pedidos
     * simultâneos passarem pela conferência, e o segundo esbarraria na chave única como erro.
     */
    @Transactional
    @Modifying
    @Query(
        value = "INSERT INTO feriados_ignorados (usuario_id, data) VALUES (:usuarioId, :data) ON CONFLICT DO NOTHING",
        nativeQuery = true
    )
    void ignorar(Long usuarioId, LocalDate data);

    @Transactional
    @Modifying
    @Query("DELETE FROM FeriadoIgnoradoJpaEntity f WHERE f.usuarioId = :usuarioId AND f.data = :data")
    void considerar(Long usuarioId, LocalDate data);
}
