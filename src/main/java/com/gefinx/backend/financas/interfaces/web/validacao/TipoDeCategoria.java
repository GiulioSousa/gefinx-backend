package com.gefinx.backend.financas.interfaces.web.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Recusa {@code TRANSFERENCIA} como tipo de categoria. O enum é compartilhado com a
 * transação de propósito — a amarração da Etapa 10 compara os dois lados e precisa que
 * sejam o mesmo tipo Java —, mas só dois dos três valores fazem sentido numa categoria.
 */
@Documented
@Constraint(validatedBy = ValidadorDeTipoDeCategoria.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TipoDeCategoria {

    String message() default "O tipo da categoria deve ser RECEITA ou DESPESA";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
