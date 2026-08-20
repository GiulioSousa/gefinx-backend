package com.financas.backend.usuarios.aplicacao;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.financas.backend.compartilhado.auditoria.Auditoria;
import com.financas.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.financas.backend.usuarios.dominio.ControleDeTentativasDeLogin.ResultadoDaTentativa;
import com.financas.backend.usuarios.dominio.RepositorioUsuario;
import com.financas.backend.usuarios.dominio.Usuario;
import com.financas.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.financas.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
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

    private static final String EMAIL = "alvo@exemplo.com";
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

    @Test
    void registraOLoginAceitoComOIdentificadorDaConta() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(codificador.matches(SENHA_DO_USUARIO, "hash-real")).thenReturn(true);

        servico.autenticar(EMAIL, SENHA_DO_USUARIO);

        assertThat(mensagens()).containsExactly("login aceito usuario=1 email=" + EMAIL);
    }

    @Test
    void distingueSenhaIncorretaDeContaInexistente() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(codificador.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(EMAIL, "errada"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // A resposta HTTP não distingue os dois casos, de propósito (Etapa 5). A trilha
        // distingue, porque é lida por quem opera o sistema, não por quem tentou entrar.
        assertThat(mensagens()).containsExactly(
            "login recusado usuario=1 email=" + EMAIL + " motivo=senha-incorreta"
        );
    }

    @Test
    void registraTentativaContraContaInexistente() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(EMAIL, SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(mensagens()).containsExactly(
            "login recusado email=" + EMAIL + " motivo=conta-inexistente"
        );
    }

    @Test
    void registraOBloqueioPorTentativasExcedidas() {
        when(controle.registrar(EMAIL)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(EMAIL, SENHA_DO_USUARIO))
            .isInstanceOf(TentativasExcedidasException.class);

        assertThat(mensagens()).containsExactly("login bloqueado email=" + EMAIL + " liberaEm=900s");
    }

    @Test
    void nuncaRegistraASenhaSubmetida() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(EMAIL, SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        assertThat(mensagens()).isNotEmpty();
        assertThat(mensagens()).noneMatch(mensagem -> mensagem.contains(SENHA_DO_USUARIO));
    }

    @Test
    void registraOEmailNaFormaCanonica() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar("  ALVO@Exemplo.COM ", SENHA_DO_USUARIO))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // Grafias diferentes da mesma conta precisam agrupar-se na trilha; do contrário,
        // dez tentativas contra o mesmo alvo parecem dez alvos distintos.
        assertThat(mensagens()).containsExactly(
            "login recusado email=" + EMAIL + " motivo=conta-inexistente"
        );
    }

    private List<String> mensagens() {
        return registros.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private Usuario usuario() {
        return new Usuario(1L, "Alvo", EMAIL, "hash-real", LocalDateTime.now(), LocalDateTime.now().minusDays(1));
    }
}
