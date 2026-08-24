package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.usuarios.dominio.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro de um novo usuário, a criação das suas categorias padrão e a da
 * sua conta padrão, unindo os contextos "usuarios" e "financas".
 */
@Service
public class RegistroUseCase {

    private final CadastrarUsuarioService cadastrarUsuarioService;
    private final CategoriaService categoriaService;
    private final ContaService contaService;

    public RegistroUseCase(
        CadastrarUsuarioService cadastrarUsuarioService,
        CategoriaService categoriaService,
        ContaService contaService
    ) {
        this.cadastrarUsuarioService = cadastrarUsuarioService;
        this.categoriaService = categoriaService;
        this.contaService = contaService;
    }

    @Transactional
    public Usuario executar(String nome, String email, String senha) {
        Usuario usuario = cadastrarUsuarioService.cadastrar(nome, email, senha);
        categoriaService.criarCategoriasPadrao(usuario.getId());
        // Sem conta, o usuário novo não conseguiria lançar transação alguma, porque toda
        // transação exige uma. Mesmo nome que a V7 deu às contas criadas no backfill.
        contaService.criarContaPadrao(usuario.getId());
        return usuario;
    }
}
