package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DespesaPlanejadaSpringDataRepository extends JpaRepository<DespesaPlanejadaJpaEntity, Long> {

    List<DespesaPlanejadaJpaEntity> findByUsuarioIdOrderByPrazoAscIdAsc(Long usuarioId);

    Optional<DespesaPlanejadaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);
}
