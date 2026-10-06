package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;

/** De quanto em quanto tempo uma despesa planejada volta. */
public enum Recorrencia {

    SEMANAL,
    MENSAL;

    /**
     * O prazo da ocorrência seguinte, contado do prazo da atual, e não do dia em que ela foi
     * paga: pagar com atraso não empurra o calendário, e pagar adiantado não o antecipa.
     *
     * <p>O mensal vence no {@code diaDoVencimento}, ou no último dia do mês quando ele é mais
     * curto. A âncora é o que traz o 31 de volta depois de um fevereiro.
     */
    public LocalDate proximoPrazo(LocalDate prazo, Integer diaDoVencimento) {
        if (this == SEMANAL) {
            return prazo.plusWeeks(1);
        }
        LocalDate proximoMes = prazo.withDayOfMonth(1).plusMonths(1);
        return proximoMes.withDayOfMonth(Math.min(diaDoVencimento, proximoMes.lengthOfMonth()));
    }
}
