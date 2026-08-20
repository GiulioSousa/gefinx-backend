package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin.ResultadoDaTentativa;
import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutenticacaoServiceTest {

    private static final String EMAIL = "alvo@exemplo.com";
    private static final ResultadoDaTentativa PERMITIDA = new ResultadoDaTentativa(false, 0);
    private static final ResultadoDaTentativa BLOQUEADA = new ResultadoDaTentativa(true, 900);

    private RepositorioUsuario repositorio;
    private PasswordEncoder codificador;
    private ControleDeTentativasDeLogin controle;
    private AutenticacaoService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioUsuario.class);
        codificador = mock(PasswordEncoder.class);
        controle = mock(ControleDeTentativasDeLogin.class);
        when(codificador.encode(anyString())).thenReturn("$2a$10$hashDescartavel");
        servico = new AutenticacaoService(repositorio, codificador, controle);
    }

    @Test
    void recusaComQuatroCentosEVinteENoveQuandoAContaEstaBloqueada() {
        when(controle.registrar(EMAIL)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(EMAIL, "qualquer"))
            .isInstanceOf(TentativasExcedidasException.class)
            .hasMessageContaining("900 segundos");
    }

    @Test
    void naoConsultaNemVerificaSenhaQuandoBloqueada() {
        when(controle.registrar(EMAIL)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(EMAIL, "qualquer"))
            .isInstanceOf(TentativasExcedidasException.class);

        verifyNoInteractions(repositorio);
    }

    @Test
    void contabilizaTentativaMesmoParaEmailInexistente() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(EMAIL, "qualquer"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // Contar só contas existentes faria do bloqueio um oráculo de enumeração.
        verify(controle).registrar(EMAIL);
        verify(controle, never()).liberar(anyString());
    }

    @Test
    void naoDevolveACotaQuandoASenhaEstaErrada() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("errada", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(EMAIL, "errada"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        verify(controle, never()).liberar(anyString());
    }

    @Test
    void devolveACotaAposLoginBemSucedido() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("correta", "hash-real")).thenReturn(true);

        assertThat(servico.autenticar(EMAIL, "correta").getEmail()).isEqualTo(EMAIL);

        // Sem isso, quem usa o sistema com frequência seria barrado pelo próprio uso.
        verify(controle).liberar(EMAIL);
    }


    @Test
    void encontraAContaMesmoQuandoOEmailChegaComOutraCaixa() {
        when(controle.registrar(EMAIL)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("correta", "hash-real")).thenReturn(true);

        // O banco guarda a forma canônica; sem normalizar aqui, quem digitasse com
        // maiúscula não encontraria a própria conta.
        assertThat(servico.autenticar("  ALVO@Exemplo.COM ", "correta").getEmail()).isEqualTo(EMAIL);

        verify(controle).liberar(EMAIL);
    }
    private Usuario usuario() {
        return new Usuario(1L, "Alvo", EMAIL, "hash-real", LocalDateTime.now(), LocalDateTime.now().minusDays(1));
    }
}
