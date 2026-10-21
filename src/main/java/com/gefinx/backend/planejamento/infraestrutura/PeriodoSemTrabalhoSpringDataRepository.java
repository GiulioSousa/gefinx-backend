package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PeriodoSemTrabalhoSpringDataRepository extends JpaRepository<PeriodoSemTrabalhoJpaEntity, Long> {

    List<PeriodoSemTrabalhoJpaEntity> findByUsuarioIdOrderByInicioAscIdAsc(Long usuarioId);

    /** Com o dono no WHERE: o id de outra pessoa apaga nada, e volta zero. */
    @Transactional
    @Modifying
    @Query("DELETE FROM PeriodoSemTrabalhoJpaEntity p WHERE p.id = :id AND p.usuarioId = :usuarioId")
    int excluirDoUsuario(Long usuarioId, Long id);
}
