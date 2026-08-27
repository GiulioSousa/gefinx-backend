package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.aplicacao.ResultadoSaldo;

import java.math.BigDecimal;

/**
 * @param totalTransferencias assinado, e sempre zero no consolidado — transferência
 *                            acontece entre contas do mesmo usuário, então o líquido no
 *                            nível dele é zero. Na leitura por conta, é o que entrou menos
 *                            o que saiu. Fica num campo próprio, e não somado às receitas
 *                            e despesas, porque dinheiro movido entre contas próprias não
 *                            é renda nem gasto: chamá-lo assim faria o extrato mentir.
 */
public record RespostaSaldo(
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal totalTransferencias,
    BigDecimal saldo
) {

    public static RespostaSaldo apartirDoResultado(ResultadoSaldo resultado) {
        return new RespostaSaldo(
            resultado.totalReceitas(),
            resultado.totalDespesas(),
            resultado.totalTransferencias(),
            resultado.saldo()
        );
    }
}
