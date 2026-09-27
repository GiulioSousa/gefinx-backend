package com.gefinx.backend.planejamento.dominio.excecoes;

/**
 * Própria do contexto, e não a de finanças: reaproveitar aquela criaria entre os dois
 * contextos justamente o acoplamento que uma extração teria de desfazer.
 */
public class RecursoNaoEncontradoNoPlanejamentoException extends RuntimeException {

    public RecursoNaoEncontradoNoPlanejamentoException(String mensagem) {
        super(mensagem);
    }
}
