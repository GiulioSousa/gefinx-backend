package com.gefinx.backend.planejamento.dominio.excecoes;

/**
 * Um período sem trabalho recusado por causa de um dos campos: o fim antes do início, longo
 * demais, sobreposto a outro, ou sem dia de trabalho dentro. Carrega o campo para a resposta
 * apontá-lo, como as falhas de validação.
 */
public class PeriodoSemTrabalhoRecusadoException extends RuntimeException {

    private final String campo;

    public PeriodoSemTrabalhoRecusadoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
