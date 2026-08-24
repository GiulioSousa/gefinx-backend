package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RepositorioCategoriaJpaAdapter implements RepositorioCategoria {

    private final CategoriaSpringDataRepository springDataRepository;

    public RepositorioCategoriaJpaAdapter(CategoriaSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Categoria salvar(Categoria categoria) {
        CategoriaJpaEntity entidade = CategoriaJpaEntity.apartirDoDominio(categoria);
        return springDataRepository.save(entidade).paraDominio();
    }

    @Override
    public List<Categoria> listarPorUsuario(Long usuarioId) {
        return springDataRepository.findByUsuarioIdOrderByNomeAsc(usuarioId).stream()
            .map(CategoriaJpaEntity::paraDominio)
            .toList();
    }

    @Override
    public Optional<Categoria> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(CategoriaJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existePorNomeTipoUsuario(String nome, TipoTransacao tipo, Long usuarioId) {
        return springDataRepository.existsByNomeIgnoreCaseAndTipoAndUsuarioId(nome, tipo, usuarioId);
    }

    @Override
    public Optional<Categoria> buscarPorNomeTipoUsuario(String nome, TipoTransacao tipo, Long usuarioId) {
        return springDataRepository.findByNomeIgnoreCaseAndTipoAndUsuarioId(nome, tipo, usuarioId)
            .map(CategoriaJpaEntity::paraDominio);
    }
}
