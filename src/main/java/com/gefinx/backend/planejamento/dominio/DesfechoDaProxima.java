package com.gefinx.backend.planejamento.dominio;

/**
 * O que aconteceu com a ocorrência seguinte ao desfazer um pagamento.
 *
 * <ul>
 *   <li>{@code NENHUMA}: não havia próxima — a despesa não se repete, ou a série foi encerrada.</li>
 *   <li>{@code REMOVIDA}: a próxima saiu do plano, porque estava como foi gerada.</li>
 *   <li>{@code MANTIDA}: a próxima ficou, porque já tinha sido editada; quem editou decide.</li>
 *   <li>{@code MANTIDA_NO_PERIODO}: a próxima ficou, porque vence dentro dos dias que o plano
 *       mostra — e ali a despesa a teria de qualquer jeito.</li>
 * </ul>
 */
public enum DesfechoDaProxima {
    NENHUMA,
    REMOVIDA,
    MANTIDA,
    MANTIDA_NO_PERIODO
}
