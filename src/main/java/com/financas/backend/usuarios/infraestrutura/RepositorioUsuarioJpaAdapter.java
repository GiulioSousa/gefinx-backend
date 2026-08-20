package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RepositorioUsuarioJpaAdapter implements RepositorioUsuario {

    private final UsuarioSpringDataRepository springDataRepository;

    public RepositorioUsuarioJpaAdapter(UsuarioSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioJpaEntity entidade = UsuarioJpaEntity.apartirDoDominio(usuario);
        return springDataRepository.save(entidade).paraDominio();
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return springDataRepository.findByEmail(email).map(UsuarioJpaEntity::paraDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return springDataRepository.findById(id).map(UsuarioJpaEntity::paraDominio);
    }

    @Override
    public boolean existePorEmail(String email) {
        return springDataRepository.existsByEmail(email);
    }
}
