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

    private static final String USUARIO = "alvo";
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
        when(controle.registrar(USUARIO)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, "qualquer"))
            .isInstanceOf(TentativasExcedidasException.class)
            .hasMessageContaining("900 segundos");
    }

    @Test
    void naoConsultaNemVerificaSenhaQuandoBloqueada() {
        when(controle.registrar(USUARIO)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, "qualquer"))
            .isInstanceOf(TentativasExcedidasException.class);

        verifyNoInteractions(repositorio);
    }

    @Test
    void contabilizaTentativaMesmoParaContaInexistente() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.autenticar(USUARIO, "qualquer"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        // Contar só contas existentes faria do bloqueio um oráculo de enumeração — e, sem
        // cadastro, sondar o login é o único jeito que sobrou de tentar enumerá-las.
        verify(controle).registrar(USUARIO);
        verify(controle, never()).liberar(anyString());
    }

    @Test
    void naoDevolveACotaQuandoASenhaEstaErrada() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("errada", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> servico.autenticar(USUARIO, "errada"))
            .isInstanceOf(CredenciaisInvalidasException.class);

        verify(controle, never()).liberar(anyString());
    }

    @Test
    void devolveACotaAposLoginBemSucedido() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("correta", "hash-real")).thenReturn(true);

        assertThat(servico.autenticar(USUARIO, "correta").getUsuario()).isEqualTo(USUARIO);

        // Sem isso, quem usa o sistema com frequência seria barrado pelo próprio uso.
        verify(controle).liberar(USUARIO);
    }

    @Test
    void encontraAContaMesmoQuandoONomeChegaComOutraCaixa() {
        when(controle.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(repositorio.buscarPorUsuario(USUARIO)).thenReturn(Optional.of(usuario()));
        when(codificador.matches("correta", "hash-real")).thenReturn(true);

        // O banco guarda a forma canônica; sem normalizar aqui, quem digitasse com
        // maiúscula não encontraria a própria conta.
        assertThat(servico.autenticar("  ALVO ", "correta").getUsuario()).isEqualTo(USUARIO);

        verify(controle).liberar(USUARIO);
    }

    private Usuario usuario() {
        return new Usuario(1L, USUARIO, "hash-real", LocalDateTime.now(), LocalDateTime.now().minusDays(1));
    }
}
