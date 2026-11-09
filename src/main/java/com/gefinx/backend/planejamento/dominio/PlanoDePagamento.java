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
 * <p><b>A meta acompanha cada lançamento</b> (Etapa 43; até a 42 ela ficava parada durante o
 * dia, calculada do saldo de ontem). Ela sai do saldo de agora e conta hoje entre os dias de
 * trabalho, quando ele é um: é "quanto ainda é preciso ganhar por dia, contando o resto de
 * hoje". Uma receita a baixa, uma despesa a sobe. Quem precisa de 300 em três dias e ganha 100
 * hoje vê a meta ir de 100 para 66,67: o que falta, 200, dividido pelo resto de hoje e pelos
 * dois dias seguintes.
 *
 * <p><b>Dois saldos.</b> A {@code falta} de cada despesa usa o {@code saldoAtual}, o dinheiro que
 * há. A meta usa o {@code saldoDaMeta}, que é o mesmo saldo com o gasto de hoje no custo
 * diário devolvido: esse gasto já está previsto na meta pela média, e descontá-lo também do
 * saldo o cobraria duas vezes. Quem calcula os dois é {@code PlanejamentoService}.
 *
 * <p><b>O {@code pendente} entra inteiro na meta.</b> Uma despesa atrasada, ou sem dia de
 * trabalho antes do prazo — vence hoje, ou é domingo e vence na segunda —, não tem por quantos
 * dias dividir: o que falta dela é pedido de uma vez, e continua somado à meta, dia após dia,
 * enquanto ela não for paga. Sai quando é paga, ou quando o saldo passa a cobri-la. Ela também
 * consome saldo em primeiro lugar, por ter o prazo mais antigo, e as seguintes dividem pelos
 * seus dias só o que sobra depois do pendente — sem isso ele seria cobrado duas vezes.
 *
 * <p><b>O custo diário soma por cima.</b> Com categorias escolhidas, o gasto médio delas por dia
 * de trabalho entra inteiro na meta, também na folga, que mostra a meta dos próximos dias.
 * Sem escolha, {@code custoDiario} é nulo e não soma nada.
 *
 * <p>{@code receitasDeHoje} é a soma das receitas do dia, sem descontar despesa nenhuma: um
 * número à parte, que a meta não usa.
 *
 * <p>{@code folgaDeHoje} diz por que hoje não é dia de trabalho — "Domingo", o nome do
 * feriado, a descrição do período — e é nulo quando é. Nesse dia a meta já se espalha só pelos
 * dias que contam.
 */
public record PlanoDePagamento(
    LocalDate hoje,
    BigDecimal saldoAtual,
    BigDecimal receitasDeHoje,
    BigDecimal metaDiaria,
    BigDecimal pendente,
    CustoDiario custoDiario,
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

    /**
     * @param saldoAtual  o dinheiro que há agora, de onde sai a falta de cada despesa
     * @param saldoDaMeta o saldo de agora com o gasto de hoje no custo diário devolvido
     * @param custoDiario nulo quando não há categoria escolhida
     */
    public static PlanoDePagamento calcular(
        LocalDate hoje,
        BigDecimal saldoAtual,
        BigDecimal saldoDaMeta,
        BigDecimal receitasDeHoje,
        CustoDiario custoDiario,
        List<DespesaPlanejada> despesas,
        CalendarioDeTrabalho calendario
    ) {
        List<DespesaPlanejada> ordenadas = despesas.stream().sorted(POR_PRAZO).toList();
        BigDecimal pendente = pendente(hoje, saldoDaMeta, ordenadas, calendario);

        List<ItemDoPlano> itens = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal maiorPorDia = BigDecimal.ZERO;
        LocalDate prazoDecisivo = null;

        for (DespesaPlanejada despesa : ordenadas) {
            acumulado = acumulado.add(despesa.getValor());
            BigDecimal falta = faltaAte(acumulado, saldoAtual);
            int dias = calendario.contarDias(hoje, despesa.getPrazo());

            BigDecimal porDiaDaMeta = porDia(
                faltaAte(acumulado, saldoDaMeta).subtract(pendente).max(BigDecimal.ZERO), dias);
            if (porDiaDaMeta != null && porDiaDaMeta.compareTo(maiorPorDia) > 0) {
                maiorPorDia = porDiaDaMeta;
                prazoDecisivo = despesa.getPrazo();
            }

            SituacaoDaDespesa situacao = situacao(despesa, falta, dias, hoje);
            itens.add(new ItemDoPlano(
                despesa,
                acumulado,
                falta,
                dias,
                situacao == SituacaoDaDespesa.EM_ANDAMENTO ? porDiaDaMeta : null,
                situacao
            ));
        }

        BigDecimal metaDiaria = pendente.add(maiorPorDia)
            .add(custoDiario == null ? BigDecimal.ZERO : custoDiario.porDia());
        String folgaDeHoje = calendario.motivoDaFolga(hoje).orElse(null);

        return new PlanoDePagamento(
            hoje, saldoAtual, receitasDeHoje, metaDiaria, pendente, custoDiario, prazoDecisivo,
            folgaDeHoje,
            List.copyOf(itens)
        );
    }

    /**
     * O que falta das despesas sem dia de trabalho até o prazo. Elas vêm antes das outras na
     * ordem do prazo, e por isso basta o acumulado da última delas.
     */
    private static BigDecimal pendente(
        LocalDate hoje,
        BigDecimal saldo,
        List<DespesaPlanejada> ordenadas,
        CalendarioDeTrabalho calendario
    ) {
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal pendente = BigDecimal.ZERO;
        for (DespesaPlanejada despesa : ordenadas) {
            acumulado = acumulado.add(despesa.getValor());
            if (calendario.contarDias(hoje, despesa.getPrazo()) == 0) {
                pendente = faltaAte(acumulado, saldo);
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
