package com.gefinx.backend.financas.dominio.excecoes;

/**
 * Pedido que o relatório não monta: meses invertidos, um intervalo longo demais, ou um tipo
 * que não se soma por categoria. Ao contrário do {@code Periodo} das somas, que trata o
 * invertido como vazio, aqui a resposta é uma linha por mês — e sem teto ela teria uma linha
 * por mês de qualquer intervalo que se pedisse.
 */
public class RelatorioInvalidoException extends RuntimeException {

    public RelatorioInvalidoException(String mensagem) {
        super(mensagem);
    }
}
