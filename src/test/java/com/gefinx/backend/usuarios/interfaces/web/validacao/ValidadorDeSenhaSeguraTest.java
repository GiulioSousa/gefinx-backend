package com.gefinx.backend.usuarios.interfaces.web.validacao;

import com.gefinx.backend.usuarios.interfaces.web.dto.RequisicaoTrocaDeSenha;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A política voltou com a troca de senha, depois de sair com o cadastro na Etapa 24. Os casos
 * são os de antes, agora sobre a nova senha da troca.
 */
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
        assertThat(mensagensPara("senha123")).contains("A senha deve ter no mínimo 10 caracteres");
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
     * O limite do BCrypt é em bytes, e em UTF-8 cada acento ocupa dois. Esta senha tem 40
     * caracteres e 80 bytes: uma checagem que contasse caracteres a deixaria passar, e o erro
     * só apareceria adiante, como falha interna.
     */
    @Test
    void recusaSenhaAcentuadaQueCabeEmCaracteresMasNaoEmBytes() {
        String senha = "ã".repeat(40);

        assertThat(senha.length()).isEqualTo(40);
        assertThat(senha.getBytes(StandardCharsets.UTF_8).length).isEqualTo(80);
        assertThat(mensagensPara(senha)).anyMatch(mensagem -> mensagem.startsWith("A senha excede 72 bytes"));
    }

    @Test
    void naoDuplicaMensagemQuandoASenhaVemEmBranco() {
        assertThat(mensagensPara(" "))
            .as("a senha em branco é assunto do @NotBlank, e só dele")
            .containsExactly("A nova senha é obrigatória");
    }

    /** A atual não passa pela política: uma antiga, curta, ainda precisa servir para trocá-la. */
    @Test
    void naoAplicaAPoliticaASenhaAtual() {
        assertThat(validador.validate(new RequisicaoTrocaDeSenha("curta", "uma frase de senha"))).isEmpty();
    }

    private List<String> mensagensPara(String novaSenha) {
        return validador.validate(new RequisicaoTrocaDeSenha("a senha atual", novaSenha)).stream()
            .map(ConstraintViolation::getMessage)
            .toList();
    }
}
