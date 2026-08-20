package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import com.financas.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AutenticacaoService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;

    public AutenticacaoService(RepositorioUsuario repositorioUsuario, PasswordEncoder codificadorDeSenha) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
    }

    public Usuario autenticar(String email, String senha) {
        Usuario usuario = repositorioUsuario.buscarPorEmail(email)
            .orElseThrow(CredenciaisInvalidasException::new);

        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        return usuario;
    }
}
