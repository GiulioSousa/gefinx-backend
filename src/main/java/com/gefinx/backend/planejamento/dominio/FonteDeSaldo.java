package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * O que o planejamento lê do contexto de finanças. Numa extração para serviços, só quem a
 * implementa muda — de chamada em processo para HTTP.
 */
public interface FonteDeSaldo {

    /**
     * O saldo acumulado até o fim de {@code dia}, inclusive, sem o dinheiro das contas de
     * fora. Lançamentos com data posterior não entram: receita agendada não é dinheiro que
     * já se tem.
     */
    BigDecimal saldoAte(Long usuarioId, LocalDate dia, Set<Long> contasDeFora);

    List<ContaDoPlanejamento> listarContas(Long usuarioId);

    /** As categorias de despesa do usuário — as que podem entrar no custo diário. */
    List<CategoriaDoPlanejamento> listarCategoriasDeDespesa(Long usuarioId);

    /** As despesas com data de {@code inicio} a {@code fim}, inclusive, de todas as contas. */
    List<Lancamento> despesasLancadas(Long usuarioId, LocalDate inicio, LocalDate fim);

    /** As receitas com data de {@code inicio} a {@code fim}, inclusive, de todas as contas. */
    List<Lancamento> receitasLancadas(Long usuarioId, LocalDate inicio, LocalDate fim);

    /** As transações informadas, com os nomes de conta e categoria. Id que não é do usuário fica de fora. */
    List<PagamentoLancado> pagamentos(Long usuarioId, Collection<Long> transacaoIds);
}
