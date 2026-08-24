package com.gefinx.backend.financas.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaSpringDataRepository extends JpaRepository<ContaJpaEntity, Long> {

    List<ContaJpaEntity> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    Optional<ContaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByNomeIgnoreCaseAndUsuarioId(String nome, Long usuarioId);
}
