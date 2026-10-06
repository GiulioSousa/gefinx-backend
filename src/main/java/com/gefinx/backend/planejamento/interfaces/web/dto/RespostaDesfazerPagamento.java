package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.DesfechoDaProxima;

/** Vai no corpo porque a tela precisa avisar quando a próxima ocorrência ficou no plano. */
public record RespostaDesfazerPagamento(DesfechoDaProxima proxima) {
}
