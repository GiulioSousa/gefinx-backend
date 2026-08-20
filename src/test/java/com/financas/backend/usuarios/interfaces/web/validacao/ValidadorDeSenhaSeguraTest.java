package com.financas.backend.usuarios.interfaces.web.validacao;

import com.financas.backend.usuarios.interfaces.web.dto.RequisicaoRegistro;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorDeSenhaSeguraTest {

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
    void aceitaSenhaLongaOSuficiente() {
        assertThat(mensagensPara("uma frase de senha")).isEmpty();
    }

    @Test
    void recusaSenhaCurta() {
        assertThat(mensagensPara("senha123"))
            .as("oito caracteres passavam pelo mínimo anterior, de seis")
            .contains("A senha deve ter no mínimo 10 caracteres");
    }

    @Test
    void recusaSenhaPrevisivel() {
        assertThat(mensagensPara("1234567890"))
            .as("tem comprimento suficiente, mas é das primeiras que um atacante tenta")
            .contains("Esta senha é comum demais e seria adivinhada rápido; escolha outra");
    }

    @Test
    void recusaSenhaPrevisivelIndependenteDaCaixa() {
        assertThat(mensagensPara("MinhaSenha")).isNotEmpty();
    }

    @Test
    void recusaSenhaAcimaDoLimiteDoBcrypt() {
        assertThat(mensagensPara("a".repeat(73)))
            .as("o BCrypt não olha além do 72º byte")
            .anyMatch(mensagem -> mensagem.startsWith("A senha excede 72 bytes"));
    }

    /**
     * O limite do BCrypt é em bytes, e em UTF-8 cada acento ocupa dois. Esta senha tem
     * 40 caracteres e 80 bytes: uma checagem que contasse caracteres a deixaria passar,
     * e o erro só apareceria adiante, como falha interna.
     */
    @Test
    void recusaSenhaAcentuadaQueCabeEmCaracteresMasNaoEmBytes() {
        String senha = "ã".repeat(40);

        assertThat(senha.length()).isEqualTo(40);
        assertThat(senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(80);
        assertThat(mensagensPara(senha))
            .anyMatch(mensagem -> mensagem.startsWith("A senha excede 72 bytes"));
    }

    @Test
    void naoDuplicaMensagemQuandoASenhaVemEmBranco() {
        assertThat(mensagensPara(" "))
            .as("a senha em branco é assunto do @NotBlank, e só dele")
            .containsExactly("A senha é obrigatória");
    }


    @Test
    void aceitaNomeExatamenteNoLimiteDaColuna() {
        assertThat(mensagensParaNome("a".repeat(120))).isEmpty();
    }

    @Test
    void recusaNomeUmCaractereAcimaDoLimite() {
        assertThat(mensagensParaNome("a".repeat(121)))
            .contains("O nome deve ter no máximo 120 caracteres");
    }

    /**
     * O teto do e-mail é 120, abaixo dos 180 da coluna: limite de produto, não espelho do
     * schema. O caminho que estourava a coluna era o domínio longo — a parte local já é
     * limitada a 64 pelo próprio {@code @Email}, por RFC.
     */
    @Test
    void recusaEmailAcimaDoTetoDeProduto() {
        String dominioLongo = "a".repeat(60) + "." + "a".repeat(60) + ".com";

        assertThat(mensagensParaEmail("x@" + dominioLongo))
            .contains("O e-mail deve ter no máximo 120 caracteres");
    }

    @Test
    void aceitaEmailCorporativoLongoDentroDoTeto() {
        assertThat(mensagensParaEmail("giulivan.cardoso.sousa@financeiro.departamento.empresa.com.br")).isEmpty();
    }

    private java.util.List<String> mensagensParaNome(String nome) {
        return validador.validate(new RequisicaoRegistro(nome, "fulano@exemplo.com", "uma frase de senha")).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }

    private java.util.List<String> mensagensParaEmail(String email) {
        return validador.validate(new RequisicaoRegistro("Fulano", email, "uma frase de senha")).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }
    private List<String> mensagensPara(String senha) {
        return validador.validate(new RequisicaoRegistro("Fulano", "fulano@exemplo.com", senha)).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }
}
