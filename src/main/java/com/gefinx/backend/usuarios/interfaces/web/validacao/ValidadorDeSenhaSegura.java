package com.gefinx.backend.usuarios.interfaces.web.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class ValidadorDeSenhaSegura implements ConstraintValidator<SenhaSegura, String> {

    static final int MINIMO_DE_CARACTERES = 10;

    /**
     * Teto do próprio BCrypt, que não olha além do 72º byte.
     *
     * <p>O limite é em <b>bytes</b>, não em caracteres, e isso pesa mais em português
     * do que em inglês: em UTF-8 cada acento ocupa dois bytes, de modo que uma frase
     * de senha acentuada estoura o orçamento bem antes do que a contagem de letras
     * sugere. Medir caracteres aqui deixaria passar senhas que o BCrypt recusaria
     * adiante, trocando um `400` explicativo por um erro interno.
     */
    static final int MAXIMO_DE_BYTES = 72;

    /**
     * Piso mínimo contra as escolhas mais previsíveis — não um corpus de vazamentos.
     * Cobre o topo das listas conhecidas e os padrões locais mais repetidos. Para
     * valer de verdade, o passo seguinte é conferir contra uma base de senhas
     * vazadas; até lá, esta lista evita o pior caso a custo zero.
     */
    private static final Set<String> SENHAS_PREVISIVEIS = Set.of(
        "123456", "1234567", "12345678", "123456789", "1234567890", "0123456789",
        "123123123", "111111111", "000000000", "102030405", "121212121", "987654321",
        "senha12345", "senhasenha", "minhasenha", "senha123456", "aminhasenha",
        "password12", "password123", "passw0rd12", "qwerty12345", "qwertyuiop",
        "asdfghjkl1", "1qaz2wsx3e", "abcd123456", "admin12345", "administrador",
        "iloveyou12", "princesa12", "brasil12345", "flamengo12", "corinthians",
        "palmeiras1", "saopaulo12", "familia123", "deusefiel1", "teamomuito",
        "felicidade", "jesuscristo", "meuamor123", "primeiro123", "mudar123456"
    );

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        if (senha == null || senha.isBlank()) {
            return true;
        }

        if (senha.length() < MINIMO_DE_CARACTERES) {
            return recusar(contexto, "A senha deve ter no mínimo " + MINIMO_DE_CARACTERES + " caracteres");
        }

        if (senha.getBytes(StandardCharsets.UTF_8).length > MAXIMO_DE_BYTES) {
            return recusar(contexto, "A senha excede " + MAXIMO_DE_BYTES
                + " bytes, o limite do algoritmo — lembre que cada acento ocupa dois");
        }

        if (SENHAS_PREVISIVEIS.contains(senha.toLowerCase(Locale.ROOT))) {
            return recusar(contexto, "Esta senha é comum demais e seria adivinhada rápido; escolha outra");
        }

        return true;
    }

    /**
     * A senha em branco fica para o {@code @NotBlank}, e a ausência para o próprio DTO —
     * repetir essas checagens aqui renderia duas mensagens para a mesma falha.
     */
    private boolean recusar(ConstraintValidatorContext contexto, String mensagem) {
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensagem).addConstraintViolation();
        return false;
    }
}
