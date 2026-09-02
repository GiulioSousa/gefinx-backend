package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.usuarios.dominio.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
        Base64.getEncoder().encodeToString(new byte[48]), 1440
    );

    @Test
    void gravaERecuperaOInstanteDeEmissao() {
        Instant antes = Instant.now().minusSeconds(1);

        Instant emissao = jwtService.extrairEmissao(jwtService.gerarToken(usuario()));

        // O `iat` do JWT tem precisão de segundos, daí a janela em vez de igualdade exata.
        assertThat(emissao).isBetween(antes, Instant.now().plusSeconds(1));
    }

    @Test
    void continuaRecuperandoOIdDoUsuario() {
        assertThat(jwtService.extrairIdUsuario(jwtService.gerarToken(usuario()))).isEqualTo(7L);
    }

    private Usuario usuario() {
        LocalDateTime agora = LocalDateTime.now();
        return new Usuario(7L, "fulano", "hash", agora, agora);
    }
}
