package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.Etiqueta;
import com.gefinx.backend.planejamento.dominio.RepositorioEtiquetas;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class RepositorioEtiquetasJpaAdapter implements RepositorioEtiquetas {

    private final EtiquetaSpringDataRepository springDataRepository;

    public RepositorioEtiquetasJpaAdapter(EtiquetaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public List<Etiqueta> listarPorUsuario(Long usuarioId) {
        return springDataRepository.listarDoUsuario(usuarioId).stream()
            .map(EtiquetaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<Etiqueta> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(EtiquetaJpaEntity::paraDominio);
    }

    @Override
    public boolean existeComNome(Long usuarioId, String nome, Long excetoId) {
        return excetoId == null
            ? springDataRepository.existsByUsuarioIdAndNomeIgnoreCase(usuarioId, nome)
            : springDataRepository.existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(usuarioId, nome, excetoId);
    }

    @Override
    public Etiqueta salvar(Long usuarioId, Etiqueta etiqueta) {
        return springDataRepository.save(EtiquetaJpaEntity.apartirDoDominio(usuarioId, etiqueta)).paraDominio();
    }

    @Override
    public boolean excluir(Long usuarioId, Long id) {
        return springDataRepository.excluirDoUsuario(usuarioId, id) > 0;
    }

    @Override
    public Map<Long, Long> contarDespesasPorEtiqueta(Long usuarioId) {
        return springDataRepository.contarDespesasPorEtiqueta(usuarioId).stream()
            .collect(Collectors.toMap(par -> (Long) par[0], par -> (Long) par[1]));
    }
}
