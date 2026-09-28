package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioContaJpaAdapter implements RepositorioConta {

    private final ContaSpringDataRepository springDataRepository;

    public RepositorioContaJpaAdapter(ContaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Conta salvar(Conta conta) {
        return springDataRepository.save(ContaJpaEntity.apartirDoDominio(conta)).paraDominio();
    }

    @Override
    public List<Conta> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
            .map(ContaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<Conta> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(ContaJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existePorNomeEUsuario(String nome, Long usuarioId) {
        return springDataRepository.existsByNomeIgnoreCaseAndUsuarioId(nome, usuarioId);
    }

    @Override
    public void travar(Collection<Long> contaIds) {
        if (!contaIds.isEmpty()) {
            springDataRepository.findByIdInOrderByIdAsc(contaIds);
        }
    }
}
