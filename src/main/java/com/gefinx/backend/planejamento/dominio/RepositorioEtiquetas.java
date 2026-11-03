package com.gefinx.backend.planejamento.dominio;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RepositorioEtiquetas {

    /** Em ordem de nome, sem diferenciar maiúsculas. */
    List<Etiqueta> listarPorUsuario(Long usuarioId);

    Optional<Etiqueta> buscarPorIdEUsuario(Long id, Long usuarioId);

    /** Se outra etiqueta do usuário já tem este nome, sem diferenciar maiúsculas. {@code excetoId} é a que está sendo editada. */
    boolean existeComNome(Long usuarioId, String nome, Long excetoId);

    Etiqueta salvar(Long usuarioId, Etiqueta etiqueta);

    /**
     * As despesas que a perdem ficam sem etiqueta — o banco limpa a ligação. Falso quando não
     * havia a etiqueta, ou ela era de outra pessoa: os dois dão o mesmo {@code 404}.
     */
    boolean excluir(Long usuarioId, Long id);

    /** Quantas despesas, pendentes e pagas, cada etiqueta marca. As que não marcam nenhuma ficam de fora. */
    Map<Long, Long> contarDespesasPorEtiqueta(Long usuarioId);
}
