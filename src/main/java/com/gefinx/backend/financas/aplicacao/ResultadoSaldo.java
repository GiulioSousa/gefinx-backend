package com.gefinx.backend.financas.aplicacao;

import java.math.BigDecimal;

/**
 * @param totalTransferencias assinado, e sempre zero no consolidado: transferência só
 *                            acontece entre contas do mesmo usuário — a chave estrangeira
 *                            composta da V8 não admite outra coisa —, então no nível do
 *                            usuário o que sai de uma conta entra na outra e o líquido é
 *                            zero por construção, não por aproximação.
 */
public record ResultadoSaldo(
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal totalTransferencias,
    BigDecimal saldo
) {
}
