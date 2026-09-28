package com.gefinx.backend.financas.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepositorioConta {

    Conta salvar(Conta conta);

    List<Conta> listarPorUsuario(Long usuarioId);

    Optional<Conta> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    boolean existePorNomeEUsuario(String nome, Long usuarioId);

    /**
     * Trava as contas até o fim da transação corrente. Duas operações sobre a mesma conta
     * passam a acontecer uma depois da outra — sem isso, duas despesas simultâneas conferiam
     * o saldo cada uma sem enxergar a outra, e as duas passavam.
     */
    void travar(Collection<Long> contaIds);
}
