package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.Pagina;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /** Desempate por id: duas pagas com o mesmo prazo não trocam de página entre uma busca e outra. */
    @Override
    public Pagina<DespesaPlanejada> listarPagasPorUsuario(Long usuarioId, int pagina, int tamanho) {
        Page<DespesaPlanejadaJpaEntity> resultado = springDataRepository.findByUsuarioIdAndTransacaoIdIsNotNull(
            usuarioId,
            PageRequest.of(pagina, tamanho, Sort.by(Sort.Order.desc("prazo"), Sort.Order.desc("id")))
        );
        return new Pagina<>(
            resultado.getContent().stream().map(DespesaPlanejadaJpaEntity::paraDominio).toList(),
            pagina,
            tamanho,
            resultado.getTotalElements()
        );
    }

    @Override
    public boolean temSucessora(Long id) {
        return springDataRepository.existsByAnteriorId(id);
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
