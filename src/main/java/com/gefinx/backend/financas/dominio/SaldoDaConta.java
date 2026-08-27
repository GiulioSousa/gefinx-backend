package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;

/**
 * @param totalTransferenciasLiquidas assinado: negativo pelo que saiu da conta, positivo
 *                                    pelo que entrou nela. Vem somado de propósito — uma
 *                                    transferência é uma linha só que afeta duas contas
 *                                    em sentidos opostos, e separá-lo em dois campos
 *                                    obrigaria todo leitor a lembrar de subtrair um do
 *                                    outro.
 */
public record SaldoDaConta(
    Long contaId,
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal totalTransferenciasLiquidas
) {

    public BigDecimal saldo() {
        return totalReceitas.subtract(totalDespesas).add(totalTransferenciasLiquidas);
    }
}
