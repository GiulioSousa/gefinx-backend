package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import com.financas.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacaoService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;

    /**
     * Hash descartável, conferido quando o e-mail informado não existe.
     *
     * <p>Sem ele, a resposta para um e-mail inexistente volta sem passar pelo BCrypt
     * e chega muito antes da resposta para um e-mail cadastrado — diferença grande o
     * bastante para revelar, pelo tempo, quem tem conta no sistema, ainda que a
     * mensagem de erro seja a mesma nos dois casos.
     *
     * <p>É gerado pelo próprio codificador, e não fixado no código, para acompanhar
     * sempre o custo configurado do BCrypt: um hash com custo defasado voltaria a
     * abrir a diferença de tempo que este campo existe para eliminar.
     */
    private final String hashDescartavel;

    public AutenticacaoService(RepositorioUsuario repositorioUsuario, PasswordEncoder codificadorDeSenha) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.hashDescartavel = codificadorDeSenha.encode(UUID.randomUUID().toString());
    }

    public Usuario autenticar(String email, String senha) {
        Optional<Usuario> usuarioEncontrado = repositorioUsuario.buscarPorEmail(email);

        if (usuarioEncontrado.isEmpty()) {
            codificadorDeSenha.matches(senha, hashDescartavel);
            throw new CredenciaisInvalidasException();
        }

        Usuario usuario = usuarioEncontrado.get();
        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        return usuario;
    }
}
