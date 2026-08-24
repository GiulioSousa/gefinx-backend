package com.gefinx.backend.financas.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioConta {

    Conta salvar(Conta conta);

    List<Conta> listarPorUsuario(Long usuarioId);

    Optional<Conta> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    boolean existePorNomeEUsuario(String nome, Long usuarioId);
}
