package com.gefinx.backend.usuarios.aplicacao;

import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.usuarios.dominio.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A travessia entre os contextos "usuarios" e "financas". Desde a Etapa 19 o cadastro
 * cria também a conta padrão: sem ela o usuário novo entraria num sistema em que nenhuma
 * transação pode ser lançada, porque toda transação exige conta — e a falha apareceria
 * só no primeiro lançamento, longe daqui.
 */
class RegistroUseCaseTest {

    private static final Long USUARIO = 7L;

    private CadastrarUsuarioService cadastrarUsuarioService;
    private CategoriaService categoriaService;
    private ContaService contaService;
    private RegistroUseCase useCase;

    @BeforeEach
    void preparar() {
        cadastrarUsuarioService = mock(CadastrarUsuarioService.class);
        categoriaService = mock(CategoriaService.class);
        contaService = mock(ContaService.class);
        useCase = new RegistroUseCase(cadastrarUsuarioService, categoriaService, contaService);

        when(cadastrarUsuarioService.cadastrar("Fulano", "fulano@exemplo.com", "senha-bem-longa"))
            .thenReturn(new Usuario(
                USUARIO, "Fulano", "fulano@exemplo.com", "hash", LocalDateTime.now(), LocalDateTime.now()
            ));
    }

    @Test
    void oCadastroCriaAsCategoriasPadraoEAContaPadrao() {
        useCase.executar("Fulano", "fulano@exemplo.com", "senha-bem-longa");

        verify(categoriaService).criarCategoriasPadrao(USUARIO);
        verify(contaService).criarContaPadrao(USUARIO);
    }
}
