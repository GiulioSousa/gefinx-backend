package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.Pagina;

import java.util.List;
import java.util.function.Function;

/**
 * O envelope de uma listagem paginada, no formato que o cliente lê.
 *
 * <p>É um DTO próprio, e não o {@code Page} do Spring Data serializado direto. A forma
 * JSON do {@code PageImpl} nasce da reflexão sobre os campos da classe, sem contrato
 * declarado: ela já mudou entre versões do framework e voltaria a mudar numa atualização,
 * levando junto o contrato público da API. Aqui a forma é a que este record declara, e o
 * único jeito de ela mudar é alguém editar este arquivo.
 *
 * <p>{@code totalPaginas} aparece como campo, ao contrário de {@link Pagina}, onde é
 * derivado: o cliente precisa do número para desenhar a navegação, e fazê-lo recalcular
 * uma divisão que o servidor já sabe é passar adiante uma conta que pode sair diferente.
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
