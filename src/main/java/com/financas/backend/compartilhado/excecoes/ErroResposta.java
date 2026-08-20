package com.financas.backend.compartilhado.excecoes;

import java.time.Instant;
import java.util.Map;

public record ErroResposta(Instant momento, int status, String mensagem, Map<String, String> erros) {

    public ErroResposta(int status, String mensagem) {
        this(Instant.now(), status, mensagem, Map.of());
    }

    public ErroResposta(int status, String mensagem, Map<String, String> erros) {
        this(Instant.now(), status, mensagem, erros);
    }
}
