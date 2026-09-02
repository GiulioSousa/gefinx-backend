package com.gefinx.backend.compartilhado.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param login         tentativas por endereço de origem, aplicadas no filtro
 * @param loginPorConta tentativas por conta alvo, aplicadas no caso de uso — o filtro
 *                      não enxerga o corpo da requisição, onde o nome de usuário vem
 */
@ConfigurationProperties(prefix = "gefinx.limite-requisicoes")
public record PropriedadesLimiteDeRequisicoes(Politica login, Politica loginPorConta) {

    /**
     * @param tentativas quantas requisições são permitidas dentro da janela
     * @param janela     intervalo após o qual as tentativas são restauradas
     */
    public record Politica(int tentativas, Duration janela) {
    }
}
