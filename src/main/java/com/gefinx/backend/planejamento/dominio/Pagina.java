package com.gefinx.backend.planejamento.dominio;

import java.util.List;

/**
 * Uma fatia do histórico de pagas. É uma cópia da {@code Pagina} de finanças, e a cópia é de
 * propósito: aquela fica em finanças, e não em {@code compartilhado}, para que nenhum tipo
 * prenda um contexto ao outro numa extração para serviços. Dois records de quatro campos
 * custam menos do que esse acoplamento.
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens) {

    public int totalPaginas() {
        if (tamanho <= 0) {
            return 0;
        }
        return (int) Math.ceilDiv(totalItens, tamanho);
    }
}
