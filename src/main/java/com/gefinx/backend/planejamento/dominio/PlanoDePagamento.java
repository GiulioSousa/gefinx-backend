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
 * <p><b>A meta diária é o pendente mais a maior das exigências</b>: cada prazo pede o que
 * ainda falta até ele dividido pelos dias de trabalho até ele, e é o mais apertado que manda.
 * Quem ganha a meta todo dia cumpre todos os prazos.
 *
 * <p><b>Dois saldos, com papéis diferentes.</b> A meta sai do saldo do fim de ontem e conta
 * hoje entre os dias de trabalho, quando ele é um, e por isso fica fixa durante o dia — não se mexe a cada
 * lançamento. O que se ganhou hoje aparece à parte, como a diferença entre o saldo de agora e
 * o de ontem, e o {@code restanteHoje} diz quanto dela ainda falta. A {@code falta} de cada
 * despesa, ao contrário, usa o saldo de agora: é a resposta a "quanto falta", e ela tem de
 * mudar assim que uma receita entra. Amanhã o ganho de hoje já é saldo de ontem, e a meta é
 * recalculada sem que nada precise ser guardado.
 *
 * <p><b>O {@code pendente} entra inteiro na meta.</b> Uma despesa atrasada, ou sem dia de
 * trabalho antes do prazo — vence hoje, ou é domingo e vence na segunda —, não tem por quantos
 * dias dividir: o que falta dela é pedido de uma vez, e continua somado à meta, dia após dia,
 * enquanto ela não for paga. Sai quando é paga, ou quando o saldo de ontem passa a cobri-la. Ela
 * também consome saldo em primeiro lugar, por ter o prazo mais antigo, e as seguintes dividem
 * pelos seus dias só o que sobra depois do pendente — sem isso ele seria cobrado duas vezes.
 *
 * <p>{@code folgaDeHoje} diz por que hoje não é dia de trabalho — "Domingo", o nome do
 * feriado, a descrição do período — e é nulo quando é. Nesse dia a meta não pede ganho: ela
 * já se espalha só pelos dias que contam, e o {@code restanteHoje} é zero.
 */
public record PlanoDePagamento(
    LocalDate hoje,
    BigDecimal saldoDeOntem,
    BigDecimal saldoAtual,
    BigDecimal ganhoDeHoje,
    BigDecimal metaDiaria,
    BigDecimal pendente,
    BigDecimal restanteHoje,
    LocalDate prazoDecisivo,
    String folgaDeHoje,
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
        List<DespesaPlanejada> ordenadas = despesas.stream().sorted(POR_PRAZO).toList();
        BigDecimal pendente = pendente(hoje, saldoDeOntem, ordenadas, calendario);

        List<ItemDoPlano> itens = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal maiorPorDia = BigDecimal.ZERO;
        LocalDate prazoDecisivo = null;

        for (DespesaPlanejada despesa : ordenadas) {
            acumulado = acumulado.add(despesa.getValor());
            BigDecimal falta = faltaAte(acumulado, saldoAtual);
            int dias = calendario.contarDias(hoje, despesa.getPrazo());

            // Do saldo de ontem, e não do de agora: é o que mantém a meta parada durante o
            // dia. Entra mesmo que o ganho de hoje já tenha coberto a despesa — do contrário
            // a meta cairia no meio do dia, e o restante de hoje com ela.
            BigDecimal porDiaDesdeOntem = porDia(
                faltaAte(acumulado, saldoDeOntem).subtract(pendente).max(BigDecimal.ZERO), dias);
            if (porDiaDesdeOntem != null && porDiaDesdeOntem.compareTo(maiorPorDia) > 0) {
                maiorPorDia = porDiaDesdeOntem;
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

        BigDecimal metaDiaria = pendente.add(maiorPorDia);
        BigDecimal ganhoDeHoje = saldoAtual.subtract(saldoDeOntem);
        String folgaDeHoje = calendario.motivoDaFolga(hoje).orElse(null);
        // Na folga, hoje já ficou fora da conta dos dias, e não há o que faltar dele.
        BigDecimal restanteHoje = folgaDeHoje != null
            ? BigDecimal.ZERO
            : metaDiaria.subtract(ganhoDeHoje).max(BigDecimal.ZERO);

        return new PlanoDePagamento(
            hoje, saldoDeOntem, saldoAtual, ganhoDeHoje, metaDiaria, pendente, restanteHoje, prazoDecisivo,
            folgaDeHoje,
            List.copyOf(itens)
        );
    }

    /**
     * O que falta, desde o saldo de ontem, das despesas sem dia de trabalho até o prazo. Elas
     * vêm antes das outras na ordem do prazo, e por isso basta o acumulado da última delas.
     */
    private static BigDecimal pendente(
        LocalDate hoje,
        BigDecimal saldoDeOntem,
        List<DespesaPlanejada> ordenadas,
        CalendarioDeTrabalho calendario
    ) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pendente = BigDecimal.ZERO;
        for (DespesaPlanejada despesa : ordenadas) {
            acumulado = acumulado.add(despesa.getValor());
            if (calendario.contarDias(hoje, despesa.getPrazo()) == 0) {
                pendente = faltaAte(acumulado, saldoDeOntem);
            }
        }
        return pendente;
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
     *
     * <p><b>Vence hoje vem logo depois, e pela mesma razão.</b> Uma despesa com prazo hoje e
     * saldo para ela ainda precisa ser paga hoje, e um selo verde de "coberta" diria para
     * deixar quieto no único dia em que não dá. Ela também sai de {@code SEM_DIAS_DE_TRABALHO},
     * que até aqui dizia duas coisas ao mesmo tempo — "vence hoje" e "não sobra dia útil até o
     * prazo" —, e passa a dizer só a segunda.
     */
    private static SituacaoDaDespesa situacao(DespesaPlanejada despesa, BigDecimal falta, int dias, LocalDate hoje) {
        if (despesa.getPrazo().isBefore(hoje)) {
            return SituacaoDaDespesa.ATRASADA;
        }
        if (despesa.getPrazo().isEqual(hoje)) {
            return SituacaoDaDespesa.VENCE_HOJE;
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
