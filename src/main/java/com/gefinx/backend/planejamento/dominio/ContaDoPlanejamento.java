package com.gefinx.backend.planejamento.dominio;

/**
 * O que o planejamento precisa saber de uma conta: qual é, e como se chama. A conta em si
 * pertence a finanças; aqui ela é só uma referência.
 */
public record ContaDoPlanejamento(Long id, String nome) {
}
