package com.gefinx.backend.planejamento.dominio;

import java.util.List;

public interface RepositorioPeriodosSemTrabalho {

    /** Em ordem de início. */
    List<PeriodoSemTrabalho> listarPorUsuario(Long usuarioId);

    PeriodoSemTrabalho salvar(Long usuarioId, PeriodoSemTrabalho periodo);

    /** Falso quando não havia o período, ou ele era de outra pessoa — os dois dão o mesmo {@code 404}. */
    boolean excluir(Long usuarioId, Long id);
}
