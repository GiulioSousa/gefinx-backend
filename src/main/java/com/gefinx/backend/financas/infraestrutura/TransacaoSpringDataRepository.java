package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransacaoSpringDataRepository extends JpaRepository<TransacaoJpaEntity, Long>, JpaSpecificationExecutor<TransacaoJpaEntity> {

    Optional<TransacaoJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<TransacaoJpaEntity> findByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);

    boolean existsByCategoriaId(Long categoriaId);

    /**
     * Uma linha por (ano, mês, tipo). Só receita e despesa: transferência não é renda nem gasto.
     * O período é fechado nas duas pontas — quem chama sempre o tem —, e por isso sem o
     * {@code :x IS NULL OR ...} que a Etapa 21 viu tirar a data do índice.
     */
    @Query("""
        SELECT extract(year from t.dataTransacao), extract(month from t.dataTransacao), t.tipo, sum(t.valor)
          FROM TransacaoJpaEntity t
         WHERE t.usuarioId = :usuarioId
           AND t.dataTransacao BETWEEN :inicio AND :fim
           AND t.tipo IN (:receita, :despesa)
         GROUP BY extract(year from t.dataTransacao), extract(month from t.dataTransacao), t.tipo
        """)
    List<Object[]> somarPorMes(
        @Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim,
        @Param("receita") TipoTransacao receita,
        @Param("despesa") TipoTransacao despesa
    );

    @Query("""
        SELECT t.categoriaId, sum(t.valor)
          FROM TransacaoJpaEntity t
         WHERE t.usuarioId = :usuarioId
           AND t.tipo = :tipo
           AND t.dataTransacao BETWEEN :inicio AND :fim
         GROUP BY t.categoriaId
        """)
    List<Object[]> somarPorCategoria(
        @Param("usuarioId") Long usuarioId,
        @Param("tipo") TipoTransacao tipo,
        @Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim
    );

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
