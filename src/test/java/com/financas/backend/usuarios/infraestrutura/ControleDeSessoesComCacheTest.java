package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.usuarios.dominio.ControleDeSessoes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControleDeSessoesComCacheTest {

    private static final Long USUARIO = 1L;

    private UsuarioSpringDataRepository repositorio;
    private ControleDeSessoes controle;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioSpringDataRepository.class);
        controle = new ControleDeSessoesComCache(repositorio);
    }

    @Test
    void aceitaTokenEmitidoDepoisDaMarca() {
        LocalDateTime marca = LocalDateTime.now().minusHours(1);
        marcaNoBanco(marca);

        assertThat(controle.sessaoValida(USUARIO, instanteDe(marca.plusMinutes(5)))).isTrue();
    }

    @Test
    void recusaTokenEmitidoAntesDaMarca() {
        LocalDateTime marca = LocalDateTime.now();
        marcaNoBanco(marca);

        assertThat(controle.sessaoValida(USUARIO, instanteDe(marca.minusMinutes(5)))).isFalse();
    }

    @Test
    void recusaSessaoDeUsuarioQueNaoExisteMais() {
        when(repositorio.buscarSessoesValidasApos(USUARIO)).thenReturn(Optional.empty());

        assertThat(controle.sessaoValida(USUARIO, Instant.now())).isFalse();
    }

    @Test
    void consultaOBancoUmaVezSoEnquantoOValorEstaEmCache() {
        marcaNoBanco(LocalDateTime.now().minusHours(1));

        controle.sessaoValida(USUARIO, Instant.now());
        controle.sessaoValida(USUARIO, Instant.now());
        controle.sessaoValida(USUARIO, Instant.now());

        // Sem o cache, seria uma consulta por requisição no caminho mais quente do sistema.
        verify(repositorio, times(1)).buscarSessoesValidasApos(USUARIO);
    }

    @Test
    void encerrarTodasDerrubaOCacheParaValerDeImediato() {
        marcaNoBanco(LocalDateTime.now().minusHours(1));
        Instant tokenAntigo = Instant.now();
        assertThat(controle.sessaoValida(USUARIO, tokenAntigo)).isTrue();

        controle.encerrarTodas(USUARIO);
        marcaNoBanco(LocalDateTime.now().plusSeconds(1));

        assertThat(controle.sessaoValida(USUARIO, tokenAntigo))
            .as("sem invalidar o cache, a revogação só valeria quando a entrada expirasse")
            .isFalse();
    }

    @Test
    void encerrarTodasGravaAMarcaNoBanco() {
        controle.encerrarTodas(USUARIO);

        verify(repositorio).atualizarSessoesValidasApos(eq(USUARIO), any(LocalDateTime.class));
    }

    private void marcaNoBanco(LocalDateTime marca) {
        when(repositorio.buscarSessoesValidasApos(USUARIO)).thenReturn(Optional.of(marca));
    }

    private Instant instanteDe(LocalDateTime momento) {
        return momento.atZone(ZoneId.systemDefault()).toInstant();
    }
}
