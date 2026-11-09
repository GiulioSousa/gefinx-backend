package com.gefinx.backend.planejamento.dominio;

import java.util.Set;

/** As categorias de despesa cujo gasto médio entra na meta diária. */
public interface RepositorioCategoriasDoCustoDiario {

    Set<Long> listarPorUsuario(Long usuarioId);

    /** Troca a lista inteira: a tela manda a escolha completa, e não uma diferença. */
    void substituir(Long usuarioId, Set<Long> categoriaIds);
}
