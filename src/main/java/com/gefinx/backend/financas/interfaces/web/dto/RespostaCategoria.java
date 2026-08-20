package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.TipoTransacao;

public record RespostaCategoria(Long id, String nome, TipoTransacao tipo) {

    public static RespostaCategoria apartirDoDominio(Categoria categoria) {
        return new RespostaCategoria(categoria.getId(), categoria.getNome(), categoria.getTipo());
    }
}
