package com.financas.backend.compartilhado.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "financas.limite-requisicoes")
public record PropriedadesLimiteDeRequisicoes(Politica login, Politica registro) {

    /**
     * @param tentativas quantas requisições são permitidas dentro da janela
     * @param janela     intervalo após o qual as tentativas são restauradas
     */
    public record Politica(int tentativas, Duration janela) {
    }
}
