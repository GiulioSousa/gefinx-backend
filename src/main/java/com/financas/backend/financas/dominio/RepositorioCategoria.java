package com.financas.backend.financas.dominio;

import java.util.List;
import java.util.Optional;

public interface RepositorioCategoria {

    Categoria salvar(Categoria categoria);

    List<Categoria> listarPorUsuario(Long usuarioId);

    Optional<Categoria> buscarPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    boolean existePorNomeTipoUsuario(String nome, TipoTransacao tipo, Long usuarioId);
}
