package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.usuarios.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro de um novo usuário e a criação das categorias
 * padrão para ele, unindo os contextos "usuarios" e "financas".
 */
@Service
public class RegistroUseCase {

    private final CadastrarUsuarioService cadastrarUsuarioService;
    private final CategoriaService categoriaService;

    public RegistroUseCase(CadastrarUsuarioService cadastrarUsuarioService, CategoriaService categoriaService) {
        this.cadastrarUsuarioService = cadastrarUsuarioService;
        this.categoriaService = categoriaService;
    }

    @Transactional
    public Usuario executar(String nome, String email, String senha) {
        Usuario usuario = cadastrarUsuarioService.cadastrar(nome, email, senha);
        categoriaService.criarCategoriasPadrao(usuario.getId());
        return usuario;
    }
}
