package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.Pagina;

import java.util.List;
import java.util.function.Function;

/**
 * O mesmo formato da página de transações, campo a campo, para o cliente ler as duas com um
 * tipo só. Copiada, e não importada de finanças, pelo motivo de {@link Pagina}.
 */
public record RespostaPagina<T>(
    List<T> itens,
    int pagina,
    int tamanho,
    long totalItens,
    int totalPaginas
) {

    public static <D, R> RespostaPagina<R> apartirDoDominio(Pagina<D> pagina, Function<D, R> conversor) {
        return new RespostaPagina<>(
            pagina.itens().stream().map(conversor).toList(),
            pagina.pagina(),
            pagina.tamanho(),
            pagina.totalItens(),
            pagina.totalPaginas()
        );
    }
}
