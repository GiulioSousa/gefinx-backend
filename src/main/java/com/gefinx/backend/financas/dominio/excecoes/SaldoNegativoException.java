package com.gefinx.backend.financas.dominio.excecoes;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A operação deixaria a conta com saldo negativo em algum dia. Diz qual conta, o primeiro
 * dia e quanto: com a regra valendo para toda a história, o dia que estoura pode não ser o
 * da transação — uma despesa com data antiga esbarra no saldo de então.
 */
public class SaldoNegativoException extends RuntimeException {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public SaldoNegativoException(String nomeDaConta, LocalDate dia, BigDecimal saldo) {
        super("Saldo insuficiente: a conta " + nomeDaConta + " ficaria com "
            + NumberFormat.getCurrencyInstance(PT_BR).format(saldo) + " em " + DATA.format(dia));
    }
}
