package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.aplicacao.EtiquetaService.EtiquetaEmUso;
import com.gefinx.backend.planejamento.dominio.CorDaEtiqueta;

/** {@code despesas} é quantas despesas, pendentes e pagas, a etiqueta marca: a tela avisa o número antes de excluí-la. */
public record RespostaEtiqueta(Long id, String nome, CorDaEtiqueta cor, long despesas) {

    public static RespostaEtiqueta apartirDoUso(EtiquetaEmUso emUso) {
        return new RespostaEtiqueta(emUso.etiqueta().id(), emUso.etiqueta().nome(), emUso.etiqueta().cor(), emUso.despesas());
    }
}
