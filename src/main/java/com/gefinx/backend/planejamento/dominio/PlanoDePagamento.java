package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Quanto falta para cada despesa planejada, e quanto é preciso ganhar por dia até o prazo.
 *
 * <p><b>As despesas disputam o mesmo saldo.</b> Comparar cada uma sozinha com ele erraria:
 * com R$ 1.000 e duas despesas de R$ 800, as duas pareceriam cobertas, e só uma é. Por isso
 * elas são ordenadas por prazo e o saldo é consumido em sequência — cada uma é comparada
 * com o {@code acumulado}, a soma dela com todas as que vencem antes.
 *
 * <p><b>A meta diária é a maior das exigências</b>: cada prazo pede o que ainda falta até
 * ele dividido pelos dias de trabalho até ele, e é o mais apertado que manda. Quem ganha a
 * meta todo dia cumpre todos os prazos.
 *
 * <p><b>Dois saldos, com papéis diferentes.</b> A meta sai do saldo do fim de ontem e conta
 * hoje como dia de trabalho, e por isso fica fixa durante o dia — não se mexe a cada
 * lançamento. O que se ganhou hoje aparece à parte, como a diferença entre o saldo de agora e
 * o de ontem, e o {@code restanteHoje} diz quanto dela ainda falta. A {@code falta} de cada
 * despesa, ao contrário, usa o saldo de agora: é a resposta a "quanto falta", e ela tem de
 * mudar assim que uma receita entra. Amanhã o ganho de hoje já é saldo de ontem, e a meta é
 * recalculada sem que nada precise ser guardado.
 *
 * <p>Uma despesa atrasada, ou sem dia de trabalho antes do prazo, fica fora do cálculo da
 * meta — dividir por zero não tem resposta útil. Mas continua consumindo saldo, e em primeiro
 * lugar, por ter o prazo mais antigo: ela ainda é devida, e as seguintes só enxergam o que
 * sobrar depois dela.
 */
public record PlanoDePagamento(
    LocalDate hoje,
    BigDecimal saldoDeOntem,
    BigDecimal saldoAtual,
    BigDecimal ganhoDeHoje,
    BigDecimal metaDiaria,
    BigDecimal restanteHoje,
    LocalDate prazoDecisivo,
    List<ItemDoPlano> itens
) {

    public record ItemDoPlano(
        DespesaPlanejada despesa,
        BigDecimal acumulado,
        BigDecimal falta,
        int diasDeTrabalho,
        BigDecimal porDia,
        SituacaoDaDespesa situacao
    ) {
    }

    private static final Comparator<DespesaPlanejada> POR_PRAZO = Comparator
        .comparing(DespesaPlanejada::getPrazo)
        .thenComparing(DespesaPlanejada::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    public static PlanoDePagamento calcular(
        LocalDate hoje,
        BigDecimal saldoDeOntem,
        BigDecimal saldoAtual,
        List<DespesaPlanejada> despesas,
        CalendarioDeTrabalho calendario
    ) {
        List<ItemDoPlano> itens = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal metaDiaria = BigDecimal.ZERO;
        LocalDate prazoDecisivo = null;

        for (DespesaPlanejada despesa : despesas.stream().sorted(POR_PRAZO).toList()) {
            acumulado = acumulado.add(despesa.getValor());
            BigDecimal falta = faltaAte(acumulado, saldoAtual);
            int dias = calendario.contarDias(hoje, despesa.getPrazo());

            // Do saldo de ontem, e não do de agora: é o que mantém a meta parada durante o
            // dia. Entra mesmo que o ganho de hoje já tenha coberto a despesa — do contrário
            // a meta cairia no meio do dia, e o restante de hoje com ela.
            BigDecimal porDiaDesdeOntem = porDia(faltaAte(acumulado, saldoDeOntem), dias);
            if (porDiaDesdeOntem != null && porDiaDesdeOntem.compareTo(metaDiaria) > 0) {
                metaDiaria = porDiaDesdeOntem;
                prazoDecisivo = despesa.getPrazo();
            }

            SituacaoDaDespesa situacao = situacao(despesa, falta, dias, hoje);
            itens.add(new ItemDoPlano(
                despesa,
                acumulado,
                falta,
                dias,
                situacao == SituacaoDaDespesa.EM_ANDAMENTO ? porDiaDesdeOntem : null,
                situacao
            ));
        }

        BigDecimal ganhoDeHoje = saldoAtual.subtract(saldoDeOntem);
        BigDecimal restanteHoje = metaDiaria.subtract(ganhoDeHoje).max(BigDecimal.ZERO);

        return new PlanoDePagamento(
            hoje, saldoDeOntem, saldoAtual, ganhoDeHoje, metaDiaria, restanteHoje, prazoDecisivo, List.copyOf(itens)
        );
    }

    /** Saldo negativo entra como está: é dívida, e aumenta o que falta. */
    private static BigDecimal faltaAte(BigDecimal acumulado, BigDecimal saldo) {
        return acumulado.subtract(saldo).max(BigDecimal.ZERO);
    }

    /**
     * Arredondado para cima, no centavo: quem ganha a meta tem de chegar ao valor, e o
     * arredondamento comum deixaria faltar até meio centavo por dia.
     */
    private static BigDecimal porDia(BigDecimal falta, int dias) {
        if (dias == 0 || falta.signum() == 0) {
            return null;
        }
        return falta.divide(BigDecimal.valueOf(dias), 2, RoundingMode.CEILING);
    }

    /**
     * Atrasada vem antes de coberta: ter o dinheiro não paga a conta, e uma despesa vencida
     * que aparecesse como "coberta" sairia do radar justamente quando mais pede atenção.
     */
    private static SituacaoDaDespesa situacao(DespesaPlanejada despesa, BigDecimal falta, int dias, LocalDate hoje) {
        if (despesa.getPrazo().isBefore(hoje)) {
            return SituacaoDaDespesa.ATRASADA;
        }
        if (falta.signum() == 0) {
            return SituacaoDaDespesa.COBERTA;
        }
        if (dias == 0) {
            return SituacaoDaDespesa.SEM_DIAS_DE_TRABALHO;
        }
        return SituacaoDaDespesa.EM_ANDAMENTO;
    }
}
