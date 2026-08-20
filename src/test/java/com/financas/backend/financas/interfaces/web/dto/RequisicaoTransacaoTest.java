package com.financas.backend.financas.interfaces.web.dto;

import com.financas.backend.financas.dominio.TipoTransacao;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o limite de {@code valor} contra o que a coluna {@code NUMERIC(14, 2)} comporta.
 *
 * <p>Sem esse limite o banco decidia sozinho: arredondava a escala em silêncio — a API
 * respondia `201` ecoando o valor enviado enquanto gravava outro — e estourava em erro
 * interno quando a parte inteira não cabia.
 */
class RequisicaoTransacaoTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void prepararValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void encerrarValidador() {
        fabrica.close();
    }

    @Test
    void aceitaValorComDuasCasasDecimais() {
        assertThat(validar(new BigDecimal("1234.56"))).isEmpty();
    }

    @Test
    void recusaValorComMaisDeDuasCasasDecimais() {
        assertThat(mensagensPara(new BigDecimal("10.999999")))
            .as("o excesso de casas era arredondado pelo banco sem que o cliente soubesse")
            .contains("O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais");
    }

    @Test
    void recusaValorAcimaDoQueAColunaComporta() {
        assertThat(mensagensPara(new BigDecimal("9999999999999.00")))
            .as("a parte inteira acima de 12 dígitos estourava em erro interno")
            .contains("O valor deve ter no máximo 12 dígitos inteiros e 2 casas decimais");
    }

    @Test
    void continuaRecusandoValorNaoPositivo() {
        assertThat(mensagensPara(BigDecimal.ZERO)).contains("O valor deve ser positivo");
    }


    @Test
    void aceitaDescricaoExatamenteNoLimiteDaColuna() {
        assertThat(validarDescricao("a".repeat(200))).isEmpty();
    }

    @Test
    void recusaDescricaoUmCaractereAcimaDoLimite() {
        assertThat(validarDescricao("a".repeat(201)))
            .as("o excesso chegava ao banco e voltava como erro interno")
            .contains("A descrição deve ter no máximo 200 caracteres");
    }

    private java.util.List<String> validarDescricao(String descricao) {
        return validador.validate(new RequisicaoTransacao(
                descricao, new BigDecimal("10.00"), TipoTransacao.DESPESA, 1L, LocalDate.of(2026, 8, 20)
            )).stream()
            .map(jakarta.validation.ConstraintViolation::getMessage)
            .toList();
    }
    private java.util.List<String> mensagensPara(BigDecimal valor) {
        return validar(valor).stream()
            .map(jakarta.validation.ConstraintViolation::getMessage)
            .toList();
    }

    private java.util.Set<jakarta.validation.ConstraintViolation<RequisicaoTransacao>> validar(BigDecimal valor) {
        return validador.validate(new RequisicaoTransacao(
            "Compra qualquer", valor, TipoTransacao.DESPESA, 1L, LocalDate.of(2026, 8, 20)
        ));
    }
}
