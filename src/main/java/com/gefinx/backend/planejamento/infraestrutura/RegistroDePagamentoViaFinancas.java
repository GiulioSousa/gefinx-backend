package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.financas.aplicacao.TransacaoService;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lança o pagamento pelo mesmo serviço da tela de transações, e não por um atalho: a conta e
 * a categoria passam pelas mesmas conferências de dono e de tipo que qualquer lançamento.
 */
@Component
public class RegistroDePagamentoViaFinancas implements RegistroDePagamento {

    private final TransacaoService transacaoService;

    public RegistroDePagamentoViaFinancas(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @Override
    public Long registrar(Long usuarioId, String descricao, BigDecimal valor, LocalDate data, Long categoriaId, Long contaId) {
        return transacaoService
            .criar(usuarioId, descricao, valor, TipoTransacao.DESPESA, categoriaId, contaId, null, data)
            .transacao()
            .getId();
    }
}
