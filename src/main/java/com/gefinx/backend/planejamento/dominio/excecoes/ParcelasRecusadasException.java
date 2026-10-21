package com.gefinx.backend.planejamento.dominio.excecoes;

/**
 * Parcelas que não fecham: só uma das duas informada, parcela acima do total, parcelas numa
 * despesa que não se repete, ou um total menor que uma parcela já paga. Carrega o campo para a
 * resposta apontá-lo, como as falhas de validação.
 */
public class ParcelasRecusadasException extends RuntimeException {

    private final String campo;

    public ParcelasRecusadasException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
