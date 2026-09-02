package com.gefinx.backend.usuarios.aplicacao;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gefinx.backend.compartilhado.auditoria.Auditoria;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin.ResultadoDaTentativa;
import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica que toda tentativa de login deixa rastro — e que a senha nunca entra nele.
 *
 * <p>Lê o que de fato chegou ao logger, e não o que o serviço pretendia registrar: uma
 * chamada de log que não chega ao destino não é trilha, e só a leitura pelo appender
 * distingue os dois casos.
 */
class TrilhaDeAutenticacaoTest {

    private static final String USUARIO = "alvo";
    private static final String SENHA_DO_USUARIO = "senha-que-nao-pode-vazar";
    private static final ResultadoDaTentativa PERMITIDA = new ResultadoDaTentativa(false, 0);
    private static final ResultadoDaTentativa BLOQUEADA = new ResultadoDaTentativa(true, 900);

    private Logger loggerDaTrilha;
    private ListAppender<ILoggingEvent> registros;

    private RepositorioUsuario repositorio;
    private PasswordEncoder codificador;
    private ControleDeTentativasDeLogin controle;
    private AutenticacaoService servico;

    @BeforeEach
    void preparar() {
        registros = new ListAppender<>();
        registros.start();
        loggerDaTrilha = (Logger) LoggerFactory.getLogger(Auditoria.NOME);
        loggerDaTrilha.addAppender(registros);

        repositorio = mock(RepositorioUsuario.class);
        codificador = mock(PasswordEncoder.class);
        controle = mock(ControleDeTentativasDeLogin.class);
        when(codificador.encode(anyString())).thenReturn("$2a$10$hashDescartavel");
        servico = new AutenticacaoService(repositorio, codificador, controle);
    }

    @AfterEach
    void limpar() {
        loggerDaTrilha.detachAppender(registros);
    }

    /**
     * O id da conta sai como {@code id=}, e não como {@code usuario=}, desde que o nome de
     * usuário virou o identificador de login: as duas coisas na mesma chave tornariam a
     * trilha ambígua justamente para quem a lê procurando uma conta.
     */
    @Test
    void registraOLoginAceitoComOIdentificadorDaConta() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(usuario()));
        when(codificador.matches(SENHA_DO_USUARIO, "hash-real")).thenReturn(true);

        servico.autenticar(USUARIO, SENHA_DO_USUARIO);

        assertThat(mensagens()).containsExactly("login aceito id=1 usuario=" + USUARIO);
    }

    @Test
    void distingueSenhaIncorretaDeContaInexistente() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(usuario()));
        when(codificador.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, "errada"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // A resposta HTTP não distingue os dois casos, de propósito (Etapa 5). A trilha
        // distingue, porque é lida por quem opera o sistema, não por quem tentou entrar.
        assertThat(mensagens()).containsExactly(
            "login recusado id=1 usuario=" + USUARIO + " motivo=senha-incorreta"
        );
    }

    @Test
    void registraTentativaContraContaInexistente() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(mensagens()).containsExactly(
            "login recusado usuario=" + USUARIO + " motivo=conta-inexistente"
        );
    }

    @Test
    void registraOBloqueioPorTentativasExcedidas() {
        when(controle.registrar(USUARIO)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA_DO_USUARIO))
            .isInstanceOf(TentativasExcedidasException.class);

        assertThat(mensagens()).containsExactly("login bloqueado usuario=" + USUARIO + " liberaEm=900s");
    }

    @Test
    void nuncaRegistraASenhaSubmetida() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(USUARIO, SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(mensagens()).isNotEmpty();
        assertThat(mensagens()).noneMatch(mensagem -> mensagem.contains(SENHA_DO_USUARIO));
    }

    @Test
    void registraONomeDeUsuarioNaFormaCanonica() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar("  ALVO ", SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // Grafias diferentes da mesma conta precisam agrupar-se na trilha; do contrário,
        // dez tentativas contra o mesmo alvo parecem dez alvos distintos.
        assertThat(mensagens()).containsExactly(
            "login recusado usuario=" + USUARIO + " motivo=conta-inexistente"
        );
    }

    private List<String> mensagens() {
        return registros.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private Usuario usuario() {
        return new Usuario(1L, USUARIO, "hash-real", LocalDateTime.now(), LocalDateTime.now().minusDays(1));
    }
}
