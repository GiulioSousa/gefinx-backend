package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioDespesaPlanejadaJpaAdapter implements RepositorioDespesaPlanejada {

    private final DespesaPlanejadaSpringDataRepository springDataRepository;

    public RepositorioDespesaPlanejadaJpaAdapter(DespesaPlanejadaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public DespesaPlanejada salvar(DespesaPlanejada despesa) {
        return springDataRepository.save(DespesaPlanejadaJpaEntity.apartirDoDominio(despesa)).paraDominio();
    }

    @Override
    public List<DespesaPlanejada> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByPrazoAscIdAsc(usuarioId).stream()
            .map(DespesaPlanejadaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<DespesaPlanejada> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(DespesaPlanejadaJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }
}
