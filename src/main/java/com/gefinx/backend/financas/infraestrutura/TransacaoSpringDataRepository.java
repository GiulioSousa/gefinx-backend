package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.SaldoDaConta;
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

    boolean existsByContaId(Long contaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoJpaEntity t WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo")
    BigDecimal somarValorPorUsuarioETipo(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoTransacao tipo);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoJpaEntity t WHERE t.contaId = :contaId AND t.tipo = :tipo")
    BigDecimal somarValorPorContaETipo(@Param("contaId") Long contaId, @Param("tipo") TipoTransacao tipo);

    /**
     * Uma consulta agrupada em vez de duas por conta. Contas sem transação alguma não
     * aparecem no resultado — quem consome preenche o zero, porque um LEFT JOIN a partir
     * de transacoes não alcançaria a conta vazia de qualquer forma.
     */
    @Query("""
        SELECT new com.gefinx.backend.financas.dominio.SaldoDaConta(
            t.contaId,
            COALESCE(SUM(CASE WHEN t.tipo = :receita THEN t.valor ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.tipo = :despesa THEN t.valor ELSE 0 END), 0))
        FROM TransacaoJpaEntity t
        WHERE t.usuarioId = :usuarioId
        GROUP BY t.contaId
        """)
    List<SaldoDaConta> resumirSaldoPorConta(
        @Param("usuarioId") Long usuarioId,
        @Param("receita") TipoTransacao receita,
        @Param("despesa") TipoTransacao despesa
    );
}
