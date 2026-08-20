package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O nome cabe em `VARCHAR(80)`. Sem o teto na borda, o excesso chegava ao banco e a
 * violação voltava como `500` — comprovado com 81 caracteres, um a mais que a coluna.
 */
class RequisicaoCategoriaTest {

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
    void aceitaNomeExatamenteNoLimiteDaColuna() {
        assertThat(mensagensPara("a".repeat(80))).isEmpty();
    }

    @Test
    void recusaNomeUmCaractereAcimaDoLimite() {
        assertThat(mensagensPara("a".repeat(81)))
            .contains("O nome deve ter no máximo 80 caracteres");
    }

    /**
     * `VARCHAR(n)` no PostgreSQL conta caracteres, não bytes — diferente do BCrypt, cujo
     * teto de 72 bytes obrigou a medir em bytes na política de senha. Aqui os 80 acentuados
     * cabem, ainda que ocupem 160 bytes.
     */
    @Test
    void contaCaracteresENaoBytes() {
        assertThat(mensagensPara("ã".repeat(80))).isEmpty();
    }

    private List<String> mensagensPara(String nome) {
        return validador.validate(new RequisicaoCategoria(nome, TipoTransacao.DESPESA)).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }
}
