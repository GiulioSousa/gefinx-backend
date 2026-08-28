package com.gefinx.backend.financas.dominio;

import java.util.List;

/**
 * Uma fatia de um resultado maior, com o suficiente para o cliente saber onde está.
 *
 * <p>Existe para que a porta {@link RepositorioTransacao} possa devolver resultado
 * paginado sem que {@code Page} e {@code Pageable} do Spring Data atravessem a fronteira
 * do domínio. A regra da camada é que ela não dependa de Spring nem de JPA, e paginação
 * não é motivo para abrir exceção: a tradução entre este tipo e o do framework cabe ao
 * adaptador de infraestrutura, que é quem já conhece os dois lados.
 *
 * <p>Fica em {@code financas} e não em {@code compartilhado} de propósito. Hoje só este
 * contexto pagina, e o objetivo de médio prazo é extrair contextos como serviços
 * independentes — um tipo compartilhado entre eles seria exatamente o acoplamento que a
 * extração teria de desfazer depois.
 */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens) {

    /**
     * Derivado, e não guardado num campo: dois números que precisam concordar entre si
     * acabam discordando. Com o total de itens e o tamanho já presentes, guardar o total
     * de páginas seria criar uma terceira fonte para o que os outros dois já dizem.
     */
    public int totalPaginas() {
        if (tamanho <= 0) {
            return 0;
        }
        return (int) Math.ceilDiv(totalItens, tamanho);
    }
}
