package com.gefinx.backend.financas.interfaces.web.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reúne num ponto só a coerência entre {@code tipo}, {@code categoriaId} e
 * {@code contaDestinoId}, que nenhuma anotação de campo alcança sozinha — cada regra
 * depende do valor de outro campo.
 *
 * <p>Fica na borda, e não no caso de uso, porque é checagem pura: não precisa consultar
 * o banco para decidir. O que precisa — se a categoria e as contas existem e são de quem
 * pediu — continua no {@code TransacaoService}.
 */
@Documented
@Constraint(validatedBy = ValidadorDeTransferenciaValida.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransferenciaValida {

    String message() default "Transferência inconsistente";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
