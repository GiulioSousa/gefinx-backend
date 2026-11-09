package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * O gasto do dia a dia — alimentação, combustível, o que se escolher — que a meta diária tem
 * de cobrir além das despesas planejadas, olhando os últimos 30 dias, de {@code inicio} a
 * {@code fim}, inclusive. Hoje fica de fora, para que a média não mude a cada gasto do dia.
 *
 * <p><b>A média de cada categoria é pelos dias em que houve gasto nela</b> (pedido do dono,
 * Etapa 43): combustível em 15 dos 30 dias soma esses 15 e divide por 15. É o quanto se gasta
 * num dia em que se gasta. O {@code porDia} do custo é a soma das médias das categorias, e entra
 * inteiro na meta de todo dia. Categoria sem gasto na janela fica com zero.
 *
 * <p>Não é uma despesa planejada: não tem prazo nem pagamento, porque o gasto de verdade já é
 * lançado como transação. Gravá-la como uma pediria pagar de novo o que já saiu.
 */
public record CustoDiario(
    LocalDate inicio,
    LocalDate fim,
    BigDecimal total,
    BigDecimal porDia,
    List<MediaDaCategoria> categorias
) {

    public static final int JANELA_EM_DIAS = 30;

    /** O que se gastou numa categoria na janela, em quantos dias distintos, e a média por esses dias. */
    public record MediaDaCategoria(Long categoriaId, BigDecimal total, int diasComGasto, BigDecimal porDia) {
    }

    /**
     * @param categorias as escolhidas, cada uma com a sua média, mesmo sem gasto
     * @param gastos     já filtrados: nas contas do planejamento, sem pagamento de despesa
     *                   planejada e dentro da janela, antes de hoje
     */
    public static CustoDiario calcular(LocalDate hoje, Set<Long> categorias, List<Lancamento> gastos) {
        List<MediaDaCategoria> medias = categorias.stream()
            .sorted()
            .map(categoriaId -> media(categoriaId, gastos.stream()
                .filter(gasto -> categoriaId.equals(gasto.categoriaId()))
                .toList()))
            .sorted(Comparator.comparing(MediaDaCategoria::porDia).reversed())
            .toList();
        return new CustoDiario(
            inicioDaJanela(hoje),
            hoje.minusDays(1),
            medias.stream().map(MediaDaCategoria::total).reduce(BigDecimal.ZERO, BigDecimal::add),
            medias.stream().map(MediaDaCategoria::porDia).reduce(BigDecimal.ZERO, BigDecimal::add),
            medias
        );
    }

    /** Arredondada para cima, como o por dia das despesas: quem ganha a meta tem de chegar ao valor. */
    private static MediaDaCategoria media(Long categoriaId, List<Lancamento> gastos) {
        BigDecimal total = gastos.stream().map(Lancamento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        int dias = (int) gastos.stream().map(Lancamento::data).distinct().count();
        BigDecimal porDia = dias == 0
            ? BigDecimal.ZERO
            : total.divide(BigDecimal.valueOf(dias), 2, RoundingMode.CEILING);
        return new MediaDaCategoria(categoriaId, total, dias, porDia);
    }

    public static LocalDate inicioDaJanela(LocalDate hoje) {
        return hoje.minusDays(JANELA_EM_DIAS);
    }
}
