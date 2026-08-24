package com.gefinx.backend.financas.interfaces.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequisicaoContaTest {

    private static Validator validador;

    @BeforeAll
    static void prepararValidador() {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            validador = fabrica.getValidator();
        }
    }

    @Test
    void aceitaContaSemSaldoInicial() {
        assertThat(mensagens("Carteira", null)).isEmpty();
    }

    @Test
    void aceitaSaldoInicialZero() {
        assertThat(mensagens("Carteira", BigDecimal.ZERO)).isEmpty();
    }

    /**
     * A abertura é uma receita, e conta que nasce devendo é cartão de crédito — que tem
     * fatura e limite, e está fora deste desenho.
     */
    @Test
    void recusaSaldoInicialNegativo() {
        assertThat(mensagens("Carteira", new BigDecimal("-1.00")))
            .contains("O saldo inicial não pode ser negativo");
    }

    /** Mesmo limite de `valor` em transações: é numa transação que ele vai parar. */
    @Test
    void recusaSaldoInicialComCasasDecimaisAMais() {
        assertThat(mensagens("Carteira", new BigDecimal("10.999999")))
            .contains("O saldo inicial deve ter no máximo 12 dígitos inteiros e 2 casas decimais");
    }

    @Test
    void exigeNome() {
        assertThat(mensagens("   ", null)).contains("O nome é obrigatório");
    }

    @Test
    void aceitaNomeExatamenteNoLimiteDaColuna() {
        assertThat(mensagens("a".repeat(80), null)).isEmpty();
    }

    @Test
    void recusaNomeUmCaractereAcimaDoLimite() {
        assertThat(mensagens("a".repeat(81), null))
            .as("o excesso chegaria ao banco e voltaria como erro interno")
            .contains("O nome deve ter no máximo 80 caracteres");
    }

    private List<String> mensagens(String nome, BigDecimal saldoInicial) {
        return validador.validate(new RequisicaoConta(nome, saldoInicial)).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }
}
