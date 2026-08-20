package com.financas.backend.usuarios.dominio;

import java.util.Optional;

public interface RepositorioUsuario {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(Long id);

    boolean existePorEmail(String email);
}
