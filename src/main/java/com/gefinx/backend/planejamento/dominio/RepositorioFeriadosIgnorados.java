package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;
import java.util.Set;

/** Os feriados nacionais em que quem usa decidiu trabalhar. */
public interface RepositorioFeriadosIgnorados {

    Set<LocalDate> listarPorUsuario(Long usuarioId);

    /** Ignorar de novo não muda nada, nem quando dois pedidos chegam juntos. */
    void ignorar(Long usuarioId, LocalDate data);

    /** Desfaz o {@link #ignorar}; sem efeito se o feriado não estava ignorado. */
    void considerar(Long usuarioId, LocalDate data);
}
