package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DespesaPlanejadaSpringDataRepository extends JpaRepository<DespesaPlanejadaJpaEntity, Long> {

    List<DespesaPlanejadaJpaEntity> findByUsuarioIdAndTransacaoIdIsNullOrderByPrazoAscIdAsc(Long usuarioId);

    Optional<DespesaPlanejadaJpaEntity> findByIdAndUsuarioIdAndTransacaoIdIsNull(Long id, Long usuarioId);

    @Query("""
        SELECT d.transacaoId FROM DespesaPlanejadaJpaEntity d
         WHERE d.usuarioId = :usuarioId AND d.transacaoId IN :transacaoIds
        """)
    List<Long> buscarPagamentosEntre(Long usuarioId, Collection<Long> transacaoIds);
}
