package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface EtiquetaSpringDataRepository extends JpaRepository<EtiquetaJpaEntity, Long> {

    @Query("SELECT e FROM EtiquetaJpaEntity e WHERE e.usuarioId = :usuarioId ORDER BY lower(e.nome), e.id")
    List<EtiquetaJpaEntity> listarDoUsuario(Long usuarioId);

    Optional<EtiquetaJpaEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);

    /**
     * Duas consultas, e não uma com {@code :excetoId IS NULL}: o PostgreSQL recusa um parâmetro
     * nulo cujo tipo ele não consegue deduzir.
     */
    boolean existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(Long usuarioId, String nome, Long id);

    /** Com o dono no WHERE: o id de outra pessoa apaga nada, e volta zero. */
    @Transactional
    @Modifying
    @Query("DELETE FROM EtiquetaJpaEntity e WHERE e.id = :id AND e.usuarioId = :usuarioId")
    int excluirDoUsuario(Long usuarioId, Long id);

    /**
     * Pares de etiqueta e quantidade. A despesa mora no mesmo contexto, e a contagem vem numa
     * consulta só. Direto na ligação, que já traz o dono: a despesa com três etiquetas conta uma
     * vez em cada uma.
     */
    @Query(value = """
        SELECT etiqueta_id, COUNT(*) FROM despesas_planejadas_etiquetas
         WHERE usuario_id = :usuarioId
         GROUP BY etiqueta_id
        """, nativeQuery = true)
    List<Object[]> contarDespesasPorEtiqueta(Long usuarioId);
}
