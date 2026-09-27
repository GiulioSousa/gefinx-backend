package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que o planejamento escreve em finanças: a transação que paga uma despesa planejada.
 * Separada de {@link FonteDeSaldo} porque é a única escrita que atravessa os contextos, e
 * numa extração para serviços é a que pede mais cuidado — já não caberia numa transação de
 * banco só.
 */
public interface RegistroDePagamento {

    /**
     * Lança a despesa e devolve o id da transação. A validação de conta e categoria é de
     * finanças: conta ou categoria de outro usuário, ou categoria de receita, são recusadas lá.
     */
    Long registrar(Long usuarioId, String descricao, BigDecimal valor, LocalDate data, Long categoriaId, Long contaId);
}
