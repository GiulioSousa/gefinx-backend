package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.compartilhado.auditoria.Auditoria;
import com.financas.backend.usuarios.dominio.NormalizadorDeEmail;
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
        String emailNormalizado = NormalizadorDeEmail.normalizar(email);

        if (repositorioUsuario.existePorEmail(emailNormalizado)) {
            Auditoria.LOG.info("cadastro recusado email={} motivo=email-ja-cadastrado", Auditoria.seguro(emailNormalizado));
            throw new EmailJaCadastradoException(emailNormalizado);
        }

        String senhaHash = codificadorDeSenha.encode(senha);
        Usuario usuario = Usuario.novo(nome, emailNormalizado, senhaHash);
        Usuario cadastrado = repositorioUsuario.salvar(usuario);

        Auditoria.LOG.info("cadastro aceito usuario={} email={}", cadastrado.getId(), Auditoria.seguro(emailNormalizado));
        return cadastrado;
    }
}
