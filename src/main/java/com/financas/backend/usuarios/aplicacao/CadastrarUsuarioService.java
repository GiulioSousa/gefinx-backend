package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import com.financas.backend.usuarios.dominio.excecoes.EmailJaCadastradoException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CadastrarUsuarioService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;

    public CadastrarUsuarioService(RepositorioUsuario repositorioUsuario, PasswordEncoder codificadorDeSenha) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    public Usuario cadastrar(String nome, String email, String senha) {
        if (repositorioUsuario.existePorEmail(email)) {
            throw new EmailJaCadastradoException(email);
        }

        String senhaHash = codificadorDeSenha.encode(senha);
        Usuario usuario = Usuario.novo(nome, email, senhaHash);
        return repositorioUsuario.salvar(usuario);
    }
}
