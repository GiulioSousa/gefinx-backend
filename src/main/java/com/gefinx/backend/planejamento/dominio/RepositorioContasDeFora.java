package com.gefinx.backend.planejamento.dominio;

import java.util.Set;

/** As contas cujo dinheiro não conta para o planejamento. */
public interface RepositorioContasDeFora {

    Set<Long> listarPorUsuario(Long usuarioId);

    /** Troca a lista inteira: a tela manda a escolha completa, e não uma diferença. */
    void substituir(Long usuarioId, Set<Long> contaIds);
}
