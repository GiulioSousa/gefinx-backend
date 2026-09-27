package com.gefinx.backend.financas.dominio;

import java.time.LocalDate;

/**
 * O recorte de datas de uma soma, com o mesmo contrato que {@link FiltroDeTransacoes} dá à
 * listagem: as duas pontas são inclusivas, e cada uma é opcional. Sem início, a soma parte do
 * primeiro lançamento; sem fim, vai até o último; sem nenhuma das duas, é a história inteira —
 * o saldo de sempre.
 *
 * <p>Não recusa início posterior ao fim. A listagem também não recusa: um recorte invertido é
 * um recorte vazio, e o total dele é zero pelo mesmo motivo que a página dele vem sem itens.
 * Recusar só aqui faria as duas rotas discordarem sobre o mesmo pedido.
 */
public record Periodo(LocalDate inicio, LocalDate fim) {

    public static final Periodo TODA_A_HISTORIA = new Periodo(null, null);
}
