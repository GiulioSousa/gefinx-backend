package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.Recorrencia;
import com.gefinx.backend.planejamento.dominio.SituacaoDaDespesa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O plano do dia. {@code hoje} vai junto porque é o servidor quem decide o dia, pelo fuso
 * configurado, e a tela precisa dele para dizer "vence em 3 dias" sem arriscar discordar.
 *
 * <p>{@code metaDiaria} é zero quando não há o que ganhar, e {@code prazoDecisivo} nulo. Dela,
 * {@code pendente} é a parte que entra inteira, o que falta das atrasadas e das sem dia de
 * trabalho até o prazo; o resto é o maior {@code porDia}, que já desconta o pendente. O
 * {@code porDia} de um item é nulo quando ele não tem dias por onde dividir — coberto,
 * atrasado, ou sem dia de trabalho até o prazo.
 *
 * <p>{@code folgaDeHoje} é o motivo de hoje não contar — "Domingo", o nome do feriado, a
 * descrição do período —, e nulo quando hoje é dia de trabalho.
 */
public record RespostaPlanejamento(
    LocalDate hoje,
    BigDecimal saldoDeOntem,
    BigDecimal saldoAtual,
    BigDecimal ganhoDeHoje,
    BigDecimal metaDiaria,
    BigDecimal pendente,
    BigDecimal restanteHoje,
    LocalDate prazoDecisivo,
    String folgaDeHoje,
    List<Item> itens
) {

    public record Item(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Recorrencia recorrencia,
        Integer parcela,
        Integer totalDeParcelas,
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
            plano.pendente(),
            plano.restanteHoje(),
            plano.prazoDecisivo(),
            plano.folgaDeHoje(),
            plano.itens().stream()
                .map(item -> new Item(
                    item.despesa().getId(),
                    item.despesa().getDescricao(),
                    item.despesa().getValor(),
                    item.despesa().getPrazo(),
                    item.despesa().getRecorrencia(),
                    item.despesa().getParcela(),
                    item.despesa().getTotalDeParcelas(),
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
