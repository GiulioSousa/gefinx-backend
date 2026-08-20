package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import com.financas.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.financas.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutenticacaoService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ControleDeTentativasDeLogin controleDeTentativas;

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

    public AutenticacaoService(
        RepositorioUsuario repositorioUsuario,
        PasswordEncoder codificadorDeSenha,
        ControleDeTentativasDeLogin controleDeTentativas
    ) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.controleDeTentativas = controleDeTentativas;
        this.hashDescartavel = codificadorDeSenha.encode(UUID.randomUUID().toString());
    }

    /**
     * A tentativa é contabilizada antes de qualquer consulta, e a cota só é devolvida
     * ao fim de uma autenticação bem-sucedida. Ficar entre os dois pontos — falha de
     * senha ou e-mail inexistente — consome a tentativa, que é justamente o que
     * encarece a força bruta.
     *
     * <p>O bloqueio vale para qualquer e-mail submetido, exista ele ou não. Contabilizar
     * apenas contas reais transformaria a resposta de bloqueio em confirmação de que a
     * conta existe, reabrindo por outro caminho a enumeração fechada na Etapa 5.
     */
    public Usuario autenticar(String email, String senha) {
        var tentativa = controleDeTentativas.registrar(email);
        if (tentativa.bloqueado()) {
            throw new TentativasExcedidasException(tentativa.segundosParaLiberar());
        }

        Optional<Usuario> usuarioEncontrado = repositorioUsuario.buscarPorEmail(email);

        if (usuarioEncontrado.isEmpty()) {
            codificadorDeSenha.matches(senha, hashDescartavel);
            throw new CredenciaisInvalidasException();
        }

        Usuario usuario = usuarioEncontrado.get();
        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }

        controleDeTentativas.liberar(email);
        return usuario;
    }
}
