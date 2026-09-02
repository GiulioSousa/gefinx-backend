package com.gefinx.backend.usuarios.dominio;

import java.util.Optional;

public interface RepositorioUsuario {

    /** Espera o nome já na forma canônica — ver {@link NormalizadorDeUsuario}. */
    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(Long id);
}
