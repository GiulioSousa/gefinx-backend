package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.RepositorioContasDeFora;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RepositorioContasDeForaJpaAdapter implements RepositorioContasDeFora {

    private final ContaDeForaSpringDataRepository springDataRepository;

    public RepositorioContasDeForaJpaAdapter(ContaDeForaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Set<Long> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioId(usuarioId).stream()
            .map(ContaDeForaJpaEntity::getContaId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void substituir(Long usuarioId, Set<Long> contaIds) {
        springDataRepository.apagarDoUsuario(usuarioId);
        springDataRepository.saveAll(contaIds.stream()
            .map(contaId -> new ContaDeForaJpaEntity(contaId, usuarioId))
            .toList());
    }
}
