package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;
import com.gefinx.backend.planejamento.dominio.RepositorioPeriodosSemTrabalho;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RepositorioPeriodosSemTrabalhoJpaAdapter implements RepositorioPeriodosSemTrabalho {

    private final PeriodoSemTrabalhoSpringDataRepository springDataRepository;

    public RepositorioPeriodosSemTrabalhoJpaAdapter(PeriodoSemTrabalhoSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public List<PeriodoSemTrabalho> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByInicioAscIdAsc(usuarioId).stream()
            .map(PeriodoSemTrabalhoJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public PeriodoSemTrabalho salvar(Long usuarioId, PeriodoSemTrabalho periodo) {
        return springDataRepository.save(PeriodoSemTrabalhoJpaEntity.apartirDoDominio(usuarioId, periodo)).paraDominio();
    }

    @Override
    public boolean excluir(Long usuarioId, Long id) {
        return springDataRepository.excluirDoUsuario(usuarioId, id) > 0;
    }
}
