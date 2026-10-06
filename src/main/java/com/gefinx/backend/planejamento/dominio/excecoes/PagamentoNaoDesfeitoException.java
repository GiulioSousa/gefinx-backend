package com.gefinx.backend.planejamento.dominio.excecoes;

/**
 * Desfazer este pagamento deixaria o plano incoerente: a ocorrência seguinte, gerada por ele,
 * já foi paga. Desfaz-se primeiro a mais recente.
 */
public class PagamentoNaoDesfeitoException extends RuntimeException {

    public PagamentoNaoDesfeitoException(String mensagem) {
        super(mensagem);
    }
}
