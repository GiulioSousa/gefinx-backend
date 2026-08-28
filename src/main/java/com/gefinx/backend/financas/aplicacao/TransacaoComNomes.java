package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Transacao;

/**
 * Uma transação junto dos nomes das referências que ela aponta.
 *
 * <p>Existe para que a escrita não pague duas vezes pela mesma informação. Criar ou
 * atualizar uma transação já obriga o caso de uso a carregar a categoria e as contas, para
 * verificar que existem e pertencem a quem pediu — e a borda, logo depois, precisava
 * exatamente dos nomes desses mesmos registros para montar a resposta. Buscá-los de novo
 * custava até três consultas por escrita para responder o que a validação acabara de ler.
 *
 * <p>Os nomes são nulos quando o campo correspondente não existe naquele formato:
 * transferência não tem categoria, lançamento comum não tem conta de destino.
 */
public record TransacaoComNomes(
    Transacao transacao,
    String nomeCategoria,
    String nomeConta,
    String nomeContaDestino
) {
}
