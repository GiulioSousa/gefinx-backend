package com.gefinx.backend.financas.dominio.excecoes;

import com.gefinx.backend.financas.dominio.TipoTransacao;

/**
 * A categoria define se o lançamento entra ou sai; deixar a transação divergir dela
 * produz um saldo que nenhum relatório por categoria consegue explicar.
 */
public class TipoIncompativelComCategoriaException extends RuntimeException {

    public TipoIncompativelComCategoriaException(TipoTransacao tipo, String nomeDaCategoria, TipoTransacao tipoDaCategoria) {
        super("Uma transação de " + tipo + " não pode usar a categoria \"" + nomeDaCategoria
            + "\", que é de " + tipoDaCategoria);
    }
}
