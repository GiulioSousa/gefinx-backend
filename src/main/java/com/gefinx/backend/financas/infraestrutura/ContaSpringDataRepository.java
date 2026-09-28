package com.gefinx.backend.financas.infraestrutura;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContaSpringDataRepository extends JpaRepository<ContaJpaEntity, Long> {

    List<ContaJpaEntity> findByUsuarioIdOrderByNomeAsc(Long usuarioId);

    Optional<ContaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByNomeIgnoreCaseAndUsuarioId(String nome, Long usuarioId);

    /**
     * Em ordem de id: duas operações que travem as mesmas contas as pegam na mesma ordem, e
     * não ficam esperando uma pela outra em círculo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ContaJpaEntity> findByIdInOrderByIdAsc(Collection<Long> ids);
}
