package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RepositorioTransacao {

    Transacao salvar(Transacao transacao);

    List<Transacao> listarPorUsuario(Long usuarioId);

    Optional<Transacao> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    boolean existePorCategoria(Long categoriaId);

    BigDecimal somarValorPorUsuarioETipo(Long usuarioId, TipoTransacao tipo);
}
