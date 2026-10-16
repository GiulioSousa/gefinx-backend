package com.gefinx.backend.usuarios.dominio;

import java.util.Optional;

public interface RepositorioUsuario {

    /** Espera o nome já na forma canônica — ver {@link NormalizadorDeUsuario}. */
    Optional<Usuario> buscarPorUsuario(String usuario);

    Optional<Usuario> buscarPorId(Long id);

    /** Grava o hash novo; a senha em claro nunca chega aqui. */
    void alterarSenha(Long id, String senhaHash);
}
