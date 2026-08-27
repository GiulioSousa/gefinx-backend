package com.gefinx.backend.financas.interfaces.web.validacao;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidadorDeTipoDeCategoria implements ConstraintValidator<TipoDeCategoria, TipoTransacao> {

    /**
     * A coluna {@code categorias.tipo} continua em {@code VARCHAR(10)} e nem comporta a
     * palavra "TRANSFERENCIA", então o banco também recusaria. Este validador existe para
     * que a recusa chegue como `400` explicando a regra, em vez de erro interno.
     *
     * <p>Tipo ausente fica para o {@code @NotNull} do campo — repetir a checagem aqui
     * renderia duas mensagens para a mesma falha.
     */
    @Override
    public boolean isValid(TipoTransacao tipo, ConstraintValidatorContext contexto) {
        return tipo != TipoTransacao.TRANSFERENCIA;
    }
}
