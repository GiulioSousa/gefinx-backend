package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * O gasto do dia a dia — alimentação, combustível, o que se escolher — que a meta diária tem
 * de cobrir além das despesas planejadas: a média dos últimos 30 dias, de {@code inicio} a
 * {@code fim}, inclusive. Hoje fica de fora, para que a média não mude a cada gasto do dia.
 *
 * <p><b>Divide pelos dias de trabalho, e não pelos de calendário.</b> Come-se também no
 * domingo, mas só se ganha nos dias que contam: o gasto da folga tem de ser coberto por eles.
 * É o mesmo calendário do plano, com os feriados e períodos de quem usa.
 *
 * <p>Não é uma despesa planejada: não tem prazo nem pagamento, porque o gasto de verdade já é
 * lançado como transação. Gravá-la como uma pediria pagar de novo o que já saiu.
 */
public record CustoDiario(LocalDate inicio, LocalDate fim, BigDecimal total, int diasDeTrabalho, BigDecimal porDia) {

    public static final int JANELA_EM_DIAS = 30;

    /**
     * @param gastos os valores já filtrados: das categorias escolhidas, nas contas do
     *               planejamento, sem pagamento de despesa planejada e dentro da janela
     */
    public static CustoDiario calcular(LocalDate hoje, List<BigDecimal> gastos, CalendarioDeTrabalho calendario) {
        LocalDate inicio = inicioDaJanela(hoje);
        int dias = calendario.contarDias(inicio, hoje);
        BigDecimal total = gastos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Arredondado para cima, como o por dia das despesas: quem ganha a meta tem de chegar ao valor.
        BigDecimal porDia = dias == 0 || total.signum() == 0
            ? BigDecimal.ZERO
            : total.divide(BigDecimal.valueOf(dias), 2, RoundingMode.CEILING);
        return new CustoDiario(inicio, hoje.minusDays(1), total, dias, porDia);
    }

    public static LocalDate inicioDaJanela(LocalDate hoje) {
        return hoje.minusDays(JANELA_EM_DIAS);
    }
}
