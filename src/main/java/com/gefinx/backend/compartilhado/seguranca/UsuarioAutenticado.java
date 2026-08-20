package com.gefinx.backend.compartilhado.seguranca;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acessa o id do usuário autenticado (definido pelo {@link FiltroAutenticacaoJwt}
 * como principal do token de autenticação) a partir do contexto de segurança da requisição atual.
 */
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    public static Long obterId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
