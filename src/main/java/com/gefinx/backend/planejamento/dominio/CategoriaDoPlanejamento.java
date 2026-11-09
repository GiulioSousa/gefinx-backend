package com.gefinx.backend.planejamento.dominio;

/**
 * O que o planejamento precisa saber de uma categoria de despesa: qual é, e como se chama. A
 * categoria em si pertence a finanças; aqui ela é só uma referência.
 */
public record CategoriaDoPlanejamento(Long id, String nome) {
}
