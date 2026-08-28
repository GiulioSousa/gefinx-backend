package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TransacaoSpringDataRepository extends JpaRepository<TransacaoJpaEntity, Long>, JpaSpecificationExecutor<TransacaoJpaEntity> {

    Optional<TransacaoJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByCategoriaId(Long categoriaId);

    /**
     * As duas pontas, e não só {@code conta_id}: uma conta que apenas recebeu
     * transferências também precisa ficar presa contra exclusão. Checar só a origem
     * deixaria apagar a conta de destino e derrubaria a chave estrangeira da V8 —
     * ou, pior, perderia o outro lado do lançamento.
     */
    @Query("""
        SELECT COUNT(t) > 0 FROM TransacaoJpaEntity t
        WHERE t.contaId = :contaId OR t.contaDestinoId = :contaId
        """)
    boolean existePorContaOrigemOuDestino(@Param("contaId") Long contaId);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoJpaEntity t WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo")
    BigDecimal somarValorPorUsuarioETipo(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoTransacao tipo);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoJpaEntity t WHERE t.contaId = :contaId AND t.tipo = :tipo")
    BigDecimal somarValorPorContaETipo(@Param("contaId") Long contaId, @Param("tipo") TipoTransacao tipo);

    /**
     * Assinado: entra positivo no destino, sai negativo na origem. O {@code WHERE} garante
     * que a conta é uma das duas pontas, e a CHECK da V8 garante que nunca é as duas ao
     * mesmo tempo — então o {@code CASE} cobre todos os casos possíveis.
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN t.contaDestinoId = :contaId THEN t.valor ELSE -t.valor END), 0)
        FROM TransacaoJpaEntity t
        WHERE t.tipo = :transferencia
          AND (t.contaId = :contaId OR t.contaDestinoId = :contaId)
        """)
    BigDecimal somarTransferenciasLiquidasDaConta(
        @Param("contaId") Long contaId,
        @Param("transferencia") TipoTransacao transferencia
    );

    /**
     * Uma consulta agrupada em vez de três por conta.
     *
     * <p>Parte de {@code contas} e não de {@code transacoes}, e o {@code LEFT JOIN} casa
     * pelas duas pontas: uma transferência é uma linha só que afeta duas contas em
     * sentidos opostos, então ela entra no agrupamento de cada uma delas, negativa na
     * origem e positiva no destino. Partir de {@code contas} tem um segundo efeito útil —
     * conta sem transação alguma aparece com zero, em vez de sumir do resultado.
     */
    @Query("""
        SELECT new com.gefinx.backend.financas.dominio.SaldoDaConta(
            c.id,
            COALESCE(SUM(CASE WHEN t.tipo = :receita AND t.contaId = c.id THEN t.valor ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.tipo = :despesa AND t.contaId = c.id THEN t.valor ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN t.tipo = :transferencia AND t.contaDestinoId = c.id THEN t.valor
                              WHEN t.tipo = :transferencia AND t.contaId = c.id THEN -t.valor
                              ELSE 0 END), 0))
        FROM ContaJpaEntity c
        LEFT JOIN TransacaoJpaEntity t ON (t.contaId = c.id OR t.contaDestinoId = c.id)
        WHERE c.usuarioId = :usuarioId
        GROUP BY c.id
        """)
    List<SaldoDaConta> resumirSaldoPorConta(
        @Param("usuarioId") Long usuarioId,
        @Param("receita") TipoTransacao receita,
        @Param("despesa") TipoTransacao despesa,
        @Param("transferencia") TipoTransacao transferencia
    );
}
