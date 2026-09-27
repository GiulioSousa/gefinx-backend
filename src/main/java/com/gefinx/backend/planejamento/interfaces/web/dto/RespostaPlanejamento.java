package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.SituacaoDaDespesa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O plano do dia. {@code hoje} vai junto porque é o servidor quem decide o dia, pelo fuso
 * configurado, e a tela precisa dele para dizer "vence em 3 dias" sem arriscar discordar.
 *
 * <p>{@code metaDiaria} é zero quando não há o que ganhar, e {@code prazoDecisivo} nulo; o
 * {@code porDia} de cada item é nulo quando ele não entra na meta — coberto, atrasado, ou
 * sem dia de trabalho até o prazo.
 */
public record RespostaPlanejamento(
    LocalDate hoje,
    BigDecimal saldoDeOntem,
    BigDecimal saldoAtual,
    BigDecimal ganhoDeHoje,
    BigDecimal metaDiaria,
    BigDecimal restanteHoje,
    LocalDate prazoDecisivo,
    List<Item> itens
) {

    public record Item(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        BigDecimal acumulado,
        BigDecimal falta,
        int diasDeTrabalho,
        BigDecimal porDia,
        SituacaoDaDespesa situacao
    ) {
    }

    public static RespostaPlanejamento apartirDoPlano(PlanoDePagamento plano) {
        return new RespostaPlanejamento(
            plano.hoje(),
            plano.saldoDeOntem(),
            plano.saldoAtual(),
            plano.ganhoDeHoje(),
            plano.metaDiaria(),
            plano.restanteHoje(),
            plano.prazoDecisivo(),
            plano.itens().stream()
                .map(item -> new Item(
                    item.despesa().getId(),
                    item.despesa().getDescricao(),
                    item.despesa().getValor(),
                    item.despesa().getPrazo(),
                    item.acumulado(),
                    item.falta(),
                    item.diasDeTrabalho(),
                    item.porDia(),
                    item.situacao()
                ))
                .toList()
        );
    }
}
