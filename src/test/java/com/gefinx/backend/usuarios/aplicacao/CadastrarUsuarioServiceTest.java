package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.usuarios.dominio.RepositorioUsuario;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.dominio.excecoes.EmailJaCadastradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadastrarUsuarioServiceTest {

    private RepositorioUsuario repositorio;
    private CadastrarUsuarioService servico;

    @BeforeEach
    void preparar() {
        repositorio = mock(RepositorioUsuario.class);
        PasswordEncoder codificador = mock(PasswordEncoder.class);
        when(codificador.encode(anyString())).thenReturn("$2a$10$hash");
        servico = new CadastrarUsuarioService(repositorio, codificador);
    }

    @Test
    void procuraDuplicidadePelaFormaCanonicaDoEmail() {
        when(repositorio.existePorEmail("teste@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> servico.cadastrar("Sósia", "  TESTE@Exemplo.COM ", "uma frase de senha"))
            .as("na auditoria esta grafia criava uma segunda conta para a mesma pessoa")
            .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    void gravaOEmailNaFormaCanonica() {
        when(repositorio.existePorEmail(anyString())).thenReturn(false);
        when(repositorio.salvar(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        servico.cadastrar("Fulano", "  Fulano@Exemplo.COM ", "uma frase de senha");

        var capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).salvar(capturado.capture());
        assertThat(capturado.getValue().getEmail()).isEqualTo("fulano@exemplo.com");
    }
}
