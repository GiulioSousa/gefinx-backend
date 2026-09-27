package com.gefinx.backend.planejamento.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioDespesaPlanejada {

    DespesaPlanejada salvar(DespesaPlanejada despesa);

    /** Por prazo, que é a ordem em que o plano consome o saldo. */
    List<DespesaPlanejada> listarPorUsuario(Long usuarioId);

    Optional<DespesaPlanejada> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);
}
