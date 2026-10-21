package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.RepositorioFeriadosIgnorados;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RepositorioFeriadosIgnoradosJpaAdapter implements RepositorioFeriadosIgnorados {

    private final FeriadoIgnoradoSpringDataRepository springDataRepository;

    public RepositorioFeriadosIgnoradosJpaAdapter(FeriadoIgnoradoSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Set<LocalDate> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioId(usuarioId).stream()
            .map(FeriadoIgnoradoJpaEntity::getData)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void ignorar(Long usuarioId, LocalDate data) {
        springDataRepository.ignorar(usuarioId, data);
    }

    @Override
    public void considerar(Long usuarioId, LocalDate data) {
        springDataRepository.considerar(usuarioId, data);
    }
}
