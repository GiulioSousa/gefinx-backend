package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DespesaPlanejadaSpringDataRepository extends JpaRepository<DespesaPlanejadaJpaEntity, Long> {

    List<DespesaPlanejadaJpaEntity> findByUsuarioIdAndTransacaoIdIsNullOrderByPrazoAscIdAsc(Long usuarioId);

    Optional<DespesaPlanejadaJpaEntity> findByIdAndUsuarioIdAndTransacaoIdIsNull(Long id, Long usuarioId);

    Page<DespesaPlanejadaJpaEntity> findByUsuarioIdAndTransacaoIdIsNotNull(Long usuarioId, Pageable paginacao);

    boolean existsByAnteriorId(Long anteriorId);

    Optional<DespesaPlanejadaJpaEntity> findByAnteriorId(Long anteriorId);

    Optional<DespesaPlanejadaJpaEntity> findByIdAndUsuarioIdAndTransacaoIdIsNotNull(Long id, Long usuarioId);

    @Query("""
        SELECT d.transacaoId FROM DespesaPlanejadaJpaEntity d
         WHERE d.usuarioId = :usuarioId AND d.transacaoId IN :transacaoIds
        """)
    List<Long> buscarPagamentosEntre(Long usuarioId, Collection<Long> transacaoIds);
}
