package com.financas.backend.usuarios.interfaces.web.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reúne a política de senha num ponto só, em vez de espalhá-la por anotações soltas
 * no DTO: comprimento mínimo, teto imposto pelo BCrypt e recusa das senhas mais
 * previsíveis.
 *
 * <p>Deliberadamente <b>não</b> exige maiúscula, dígito ou símbolo. Regras de
 * composição empurram todo mundo para a mesma forma — {@code Senha123!} — o que reduz
 * a variedade real das senhas escolhidas enquanto aumenta o atrito. Comprimento e
 * lista de bloqueio rendem mais, conforme o NIST SP 800-63B.
 */
@Documented
@Constraint(validatedBy = ValidadorDeSenhaSegura.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaSegura {

    String message() default "Senha fora da política";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
