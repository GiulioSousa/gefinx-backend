package com.gefinx.backend.planejamento.dominio;

/**
 * O que aconteceu com a ocorrência seguinte ao desfazer um pagamento.
 *
 * <ul>
 *   <li>{@code NENHUMA}: não havia próxima — a despesa não se repete, ou a série foi encerrada.</li>
 *   <li>{@code REMOVIDA}: a próxima saiu do plano, porque estava como foi gerada.</li>
 *   <li>{@code MANTIDA}: a próxima ficou, porque já tinha sido editada; quem editou decide.</li>
 *   <li>{@code MANTIDA_PELO_ATRASO}: a próxima ficou, porque a despesa voltou atrasada — e
 *       atrasada, ela teria a seguinte no plano de qualquer jeito.</li>
 * </ul>
 */
public enum DesfechoDaProxima {
    NENHUMA,
    REMOVIDA,
    MANTIDA,
    MANTIDA_PELO_ATRASO
}
