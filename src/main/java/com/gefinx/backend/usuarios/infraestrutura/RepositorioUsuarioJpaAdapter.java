package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RepositorioUsuarioJpaAdapter implements RepositorioUsuario {

    private final UsuarioSpringDataRepository springDataRepository;

    public RepositorioUsuarioJpaAdapter(UsuarioSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Usuario> buscarPorUsuario(String usuario) {
        return springDataRepository.findByUsuario(usuario).map(UsuarioJpaEntity::paraDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return springDataRepository.findById(id).map(UsuarioJpaEntity::paraDominio);
    }
}
