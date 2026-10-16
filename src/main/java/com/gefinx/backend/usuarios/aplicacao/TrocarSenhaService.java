package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.compartilhado.auditoria.Auditoria;
import com.gefinx.backend.usuarios.dominio.ControleDeSessoes;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.gefinx.backend.usuarios.dominio.excecoes.SenhaRecusadaException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TrocarSenhaService {

    private final RepositorioUsuario repositorioUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ControleDeTentativasDeLogin controleDeTentativas;
    private final ControleDeSessoes controleDeSessoes;

    public TrocarSenhaService(
        RepositorioUsuario repositorioUsuario,
        PasswordEncoder codificadorDeSenha,
        ControleDeTentativasDeLogin controleDeTentativas,
        ControleDeSessoes controleDeSessoes
    ) {
        this.repositorioUsuario = repositorioUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.controleDeTentativas = controleDeTentativas;
        this.controleDeSessoes = controleDeSessoes;
    }

    /**
     * Troca a senha de quem está na sessão, conferindo a atual.
     *
     * <p><b>A senha atual errada conta como tentativa de login</b>, no mesmo balde da trava por
     * conta. Sem isso, quem tivesse um token roubado poderia adivinhar a senha por aqui, sem
     * limite, e tomar a conta de vez. Acertar devolve a cota, como no login.
     *
     * <p><b>Trocada a senha, todas as sessões acabam</b>, inclusive as de outros aparelhos: quem
     * troca a senha por suspeita quer justamente tirar quem entrou com a antiga. A de quem pediu
     * continua com um token novo, emitido depois da marca ({@link SenhaTrocada#sessoesValidasApos}).
     * A marca é cortada em milissegundos, a precisão do token, para o novo nunca empatar com ela.
     */
    @Transactional
    public SenhaTrocada trocar(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = repositorioUsuario.buscarPorId(usuarioId)
            .orElseThrow(CredenciaisInvalidasException::new);

        var tentativa = controleDeTentativas.registrar(usuario.getUsuario());
        if (tentativa.bloqueado()) {
            Auditoria.LOG.warn("troca de senha bloqueada id={} liberaEm={}s", usuarioId, tentativa.segundosParaLiberar());
            throw new TentativasExcedidasException(tentativa.segundosParaLiberar());
        }
        if (!codificadorDeSenha.matches(senhaAtual, usuario.getSenhaHash())) {
            Auditoria.LOG.info("troca de senha recusada id={} motivo=senha-atual-incorreta", usuarioId);
            throw new SenhaRecusadaException("senhaAtual", "A senha atual não confere");
        }
        controleDeTentativas.liberar(usuario.getUsuario());

        if (codificadorDeSenha.matches(novaSenha, usuario.getSenhaHash())) {
            throw new SenhaRecusadaException("novaSenha", "A nova senha deve ser diferente da atual");
        }

        repositorioUsuario.alterarSenha(usuarioId, codificadorDeSenha.encode(novaSenha));
        Instant marca = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        controleDeSessoes.encerrarTodasAte(usuarioId, marca);
        Auditoria.LOG.info("senha trocada id={} sessoes=encerradas", usuarioId);
        return new SenhaTrocada(usuario, marca);
    }

    /**
     * @param sessoesValidasApos a marca nova: só vale token emitido depois dela
     */
    public record SenhaTrocada(Usuario usuario, Instant sessoesValidasApos) {
    }
}
