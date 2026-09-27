package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public List<DespesaPlanejada> listarPendentesPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdAndTransacaoIdIsNullOrderByPrazoAscIdAsc(usuarioId).stream()
            .map(DespesaPlanejadaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<DespesaPlanejada> buscarPendentePorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioIdAndTransacaoIdIsNull(id, usuarioId)
            .map(DespesaPlanejadaJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    /** Lista vazia não vai ao banco: {@code IN ()} é erro de sintaxe no PostgreSQL. */
    @Override
    public Set<Long> filtrarPagamentos(Long usuarioId, Collection<Long> transacaoIds) {
        if (transacaoIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(springDataRepository.buscarPagamentosEntre(usuarioId, transacaoIds));
    }
}
