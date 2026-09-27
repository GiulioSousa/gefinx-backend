package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * O que o planejamento pede ao contexto de finanças. É a única porta entre os dois: numa
 * extração para serviços, só quem a implementa muda — de chamada em processo para HTTP.
 */
public interface FonteDeSaldo {

    /**
     * O saldo acumulado até o fim de {@code dia}, inclusive, sem o dinheiro das contas de
     * fora. Lançamentos com data posterior não entram: receita agendada não é dinheiro que
     * já se tem.
     */
    BigDecimal saldoAte(Long usuarioId, LocalDate dia, Set<Long> contasDeFora);

    List<ContaDoPlanejamento> listarContas(Long usuarioId);
}
