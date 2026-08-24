package com.gefinx.backend.financas.interfaces.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param nome         limitado ao que a coluna comporta — `VARCHAR(80)`, o mesmo teto do
 *                     nome de categoria. Sem ele, o excesso chegaria ao banco e a
 *                     violação voltaria como erro interno em vez de `400`.
 * @param saldoInicial opcional; ausente ou zero cria a conta sem transação de abertura.
 *                     Segue os mesmos limites de `valor` em transações, porque é
 *                     exatamente numa transação que ele vai parar. Negativo é recusado:
 *                     a abertura é uma receita, e conta que nasce devendo é cartão de
 *                     crédito, que está fora deste desenho.
 */
public record RequisicaoConta(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
    String nome,

    @PositiveOrZero(message = "O saldo inicial não pode ser negativo")
    @Digits(integer = 12, fraction = 2, message = "O saldo inicial deve ter no máximo 12 dígitos inteiros e 2 casas decimais")
    BigDecimal saldoInicial
) {
}
