package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.DespesaPaga;
import com.gefinx.backend.planejamento.dominio.PagamentoLancado;
import com.gefinx.backend.planejamento.dominio.Recorrencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma despesa do histórico. Os campos do pagamento vêm nulos só no caso raro descrito em
 * {@link DespesaPaga}: a transação foi excluída entre as duas leituras.
 */
public record RespostaDespesaPaga(
    Long id,
    String descricao,
    BigDecimal valorPlanejado,
    LocalDate prazo,
    Long transacaoId,
    BigDecimal valorPago,
    LocalDate dataPagamento,
    String conta,
    String categoria,
    boolean pagaComAtraso,
    Recorrencia recorrencia,
    Integer parcela,
    Integer totalDeParcelas,
    List<Long> etiquetaIds
) {

    public static RespostaDespesaPaga apartirDoDominio(DespesaPaga paga) {
        PagamentoLancado pagamento = paga.pagamento().orElse(null);
        return new RespostaDespesaPaga(
            paga.despesa().getId(),
            paga.despesa().getDescricao(),
            paga.despesa().getValor(),
            paga.despesa().getPrazo(),
            paga.despesa().getTransacaoId(),
            pagamento == null ? null : pagamento.valor(),
            pagamento == null ? null : pagamento.data(),
            pagamento == null ? null : pagamento.conta(),
            pagamento == null ? null : pagamento.categoria(),
            paga.pagaComAtraso(),
            paga.despesa().getRecorrencia(),
            paga.despesa().getParcela(),
            paga.despesa().getTotalDeParcelas(),
            paga.despesa().getEtiquetaIds()
        );
    }
}
