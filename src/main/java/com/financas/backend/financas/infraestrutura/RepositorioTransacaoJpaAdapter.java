package com.financas.backend.financas.infraestrutura;

import com.financas.backend.financas.dominio.RepositorioTransacao;
import com.financas.backend.financas.dominio.TipoTransacao;
import com.financas.backend.financas.dominio.Transacao;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioTransacaoJpaAdapter implements RepositorioTransacao {

    private final TransacaoSpringDataRepository springDataRepository;

    public RepositorioTransacaoJpaAdapter(TransacaoSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Transacao salvar(Transacao transacao) {
        TransacaoJpaEntity entidade = TransacaoJpaEntity.apartirDoDominio(transacao);
        return springDataRepository.save(entidade).paraDominio();
    }

    @Override
    public List<Transacao> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByDataTransacaoDescIdDesc(usuarioId).stream()
            .map(TransacaoJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<Transacao> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(TransacaoJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existePorCategoria(Long categoriaId) {
        return springDataRepository.existsByCategoriaId(categoriaId);
    }

    @Override
    public BigDecimal somarValorPorUsuarioETipo(Long usuarioId, TipoTransacao tipo) {
        return springDataRepository.somarValorPorUsuarioETipo(usuarioId, tipo);
    }
}
