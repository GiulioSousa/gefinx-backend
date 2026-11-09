package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.RepositorioCategoriasDoCustoDiario;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RepositorioCategoriasDoCustoDiarioJpaAdapter implements RepositorioCategoriasDoCustoDiario {

    private final CategoriaDoCustoDiarioSpringDataRepository springDataRepository;

    public RepositorioCategoriasDoCustoDiarioJpaAdapter(CategoriaDoCustoDiarioSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Set<Long> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioId(usuarioId).stream()
            .map(CategoriaDoCustoDiarioJpaEntity::getCategoriaId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void substituir(Long usuarioId, Set<Long> categoriaIds) {
        springDataRepository.apagarDoUsuario(usuarioId);
        springDataRepository.saveAll(categoriaIds.stream()
            .map(categoriaId -> new CategoriaDoCustoDiarioJpaEntity(categoriaId, usuarioId))
            .toList());
    }
}
