package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.compartilhado.auditoria.Auditoria;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.NormalizadorDeUsuario;
import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
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
     * Hash descartável, conferido quando o nome de usuário informado não existe.
     *
     * <p>Sem ele, a resposta para um nome inexistente volta sem passar pelo BCrypt
     * e chega muito antes da resposta para um nome cadastrado — diferença grande o
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
     * senha ou nome inexistente — consome a tentativa, que é justamente o que
     * encarece a força bruta.
     *
     * <p>O bloqueio vale para qualquer nome submetido, exista ele ou não. Contabilizar
     * apenas contas reais transformaria a resposta de bloqueio em confirmação de que a
     * conta existe, reabrindo por outro caminho a enumeração fechada na Etapa 5. Isso
     * pesa mais desde que o cadastro deixou de existir: sem ele, sondar o login é o
     * único jeito que sobrou de descobrir quais contas há no sistema.
     */
    public Usuario autenticar(String usuario, String senha) {
        String usuarioNormalizado = NormalizadorDeUsuario.normalizar(usuario);

        var tentativa = controleDeTentativas.registrar(usuarioNormalizado);
        if (tentativa.bloqueado()) {
            Auditoria.LOG.warn(
                "login bloqueado usuario={} liberaEm={}s",
                Auditoria.seguro(usuarioNormalizado), tentativa.segundosParaLiberar()
            );
            throw new TentativasExcedidasException(tentativa.segundosParaLiberar());
        }

        Optional<Usuario> usuarioEncontrado = repositorioUsuario.buscarPorUsuario(usuarioNormalizado);

        if (usuarioEncontrado.isEmpty()) {
            codificadorDeSenha.matches(senha, hashDescartavel);
            Auditoria.LOG.info("login recusado usuario={} motivo=conta-inexistente", Auditoria.seguro(usuarioNormalizado));
            throw new CredenciaisInvalidasException();
        }

        Usuario encontrado = usuarioEncontrado.get();
        if (!codificadorDeSenha.matches(senha, encontrado.getSenhaHash())) {
            Auditoria.LOG.info(
                "login recusado id={} usuario={} motivo=senha-incorreta",
                encontrado.getId(), Auditoria.seguro(usuarioNormalizado)
            );
            throw new CredenciaisInvalidasException();
        }

        controleDeTentativas.liberar(usuarioNormalizado);
        Auditoria.LOG.info("login aceito id={} usuario={}", encontrado.getId(), Auditoria.seguro(usuarioNormalizado));
        return encontrado;
    }
}
