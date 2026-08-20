package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransacaoSpringDataRepository extends JpaRepository<TransacaoJpaEntity, Long> {

    List<TransacaoJpaEntity> findByUsuarioIdOrderByDataTransacaoDescIdDesc(Long usuarioId);

    Optional<TransacaoJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByCategoriaId(Long categoriaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoJpaEntity t WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo")
    BigDecimal somarValorPorUsuarioETipo(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoTransacao tipo);
}
