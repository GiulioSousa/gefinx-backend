package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoriaSpringDataRepository extends JpaRepository<CategoriaJpaEntity, Long> {

    List<CategoriaJpaEntity> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    Optional<CategoriaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByNomeIgnoreCaseAndTipoAndUsuarioId(String nome, TipoTransacao tipo, Long usuarioId);

    Optional<CategoriaJpaEntity> findByNomeIgnoreCaseAndTipoAndUsuarioId(String nome, TipoTransacao tipo, Long usuarioId);
}
