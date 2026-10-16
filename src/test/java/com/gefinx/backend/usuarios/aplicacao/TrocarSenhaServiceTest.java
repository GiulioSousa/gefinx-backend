package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.usuarios.aplicacao.TrocarSenhaService.SenhaTrocada;
import com.gefinx.backend.usuarios.dominio.ControleDeSessoes;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin.ResultadoDaTentativa;
import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.SenhaRecusadaException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrocarSenhaServiceTest {

    private static final Long ID = 7L;
    private static final String USUARIO = "alvo";
    private static final String HASH_ATUAL = "$2a$10$hashAtual";
    private static final ResultadoDaTentativa PERMITIDA = new ResultadoDaTentativa(false, 0);
    private static final ResultadoDaTentativa BLOQUEADA = new ResultadoDaTentativa(true, 900);

    private RepositorioUsuario repositorio;
    private PasswordEncoder codificador;
    private ControleDeTentativasDeLogin tentativas;
    private ControleDeSessoes sessoes;
    private TrocarSenhaService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioUsuario.class);
        codificador = mock(PasswordEncoder.class);
        tentativas = mock(ControleDeTentativasDeLogin.class);
        sessoes = mock(ControleDeSessoes.class);
        servico = new TrocarSenhaService(repositorio, codificador, tentativas, sessoes);

        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(
            new Usuario(ID, USUARIO, HASH_ATUAL, LocalDateTime.now(), LocalDateTime.now())
        ));
        when(tentativas.registrar(USUARIO)).thenReturn(PERMITIDA);
        when(codificador.matches("senha atual certa", HASH_ATUAL)).thenReturn(true);
        when(codificador.encode("uma senha nova e longa")).thenReturn("$2a$10$hashNovo");
    }

    @Test
    void trocaASenhaEEncerraAsSessoesNaMarcaDevolvida() {
        SenhaTrocada trocada = servico.trocar(ID, "senha atual certa", "uma senha nova e longa");

        verify(repositorio).alterarSenha(ID, "$2a$10$hashNovo");
        verify(sessoes).encerrarTodasAte(ID, trocada.sessoesValidasApos());
        assertThat(trocada.sessoesValidasApos())
            .as("na precisão do token, para o novo poder ser emitido um milissegundo depois")
            .isEqualTo(trocada.sessoesValidasApos().truncatedTo(ChronoUnit.MILLIS));
        verify(tentativas).liberar(USUARIO);
    }

    /** A senha atual errada consome a tentativa, e nada muda. */
    @Test
    void recusaASenhaAtualErradaNoCampoDela() {
        assertThatThrownBy(() -> servico.trocar(ID, "errada", "uma senha nova e longa"))
            .isInstanceOf(SenhaRecusadaException.class)
            .extracting("campo").isEqualTo("senhaAtual");

        verify(tentativas).registrar(USUARIO);
        verify(tentativas, never()).liberar(any());
        verify(repositorio, never()).alterarSenha(anyLong(), any());
        verify(sessoes, never()).encerrarTodasAte(anyLong(), any());
    }

    /** Sem a trava, um token roubado serviria para adivinhar a senha sem limite. */
    @Test
    void respeitaATravaPorContaDoLogin() {
        when(tentativas.registrar(USUARIO)).thenReturn(BLOQUEADA);

        assertThatThrownBy(() -> servico.trocar(ID, "senha atual certa", "uma senha nova e longa"))
            .isInstanceOf(TentativasExcedidasException.class);

        verify(codificador, never()).matches(any(), any());
        verify(repositorio, never()).alterarSenha(anyLong(), any());
    }

    @Test
    void recusaANovaIgualAAtual() {
        when(codificador.matches("senha atual certa", HASH_ATUAL)).thenReturn(true);

        assertThatThrownBy(() -> servico.trocar(ID, "senha atual certa", "senha atual certa"))
            .isInstanceOf(SenhaRecusadaException.class)
            .extracting("campo").isEqualTo("novaSenha");

        verify(repositorio, never()).alterarSenha(anyLong(), any());
    }
}
