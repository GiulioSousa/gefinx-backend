package com.gefinx.backend.usuarios.dominio;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorDeUsuarioTest {

    @Test
    void reduzMaiusculasAMinusculas() {
        assertThat(NormalizadorDeUsuario.normalizar("Fulano")).isEqualTo("fulano");
    }

    @Test
    void removeEspacosNasPontas() {
        assertThat(NormalizadorDeUsuario.normalizar("  fulano  ")).isEqualTo("fulano");
    }

    @Test
    void mantemNuloComoNulo() {
        assertThat(NormalizadorDeUsuario.normalizar(null)).isNull();
    }

    @Test
    void naoAlteraNomeJaCanonico() {
        assertThat(NormalizadorDeUsuario.normalizar("fulano")).isEqualTo("fulano");
    }

    /**
     * Em turco o minúsculo de {@code I} é {@code ı}, sem pingo. Com o locale da máquina,
     * o mesmo nome normalizaria diferente conforme onde a aplicação roda, e duas
     * instâncias discordariam sobre qual conta é qual.
     */
    @Test
    void independeDoLocaleDaMaquina() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(NormalizadorDeUsuario.normalizar("FILIPE")).isEqualTo("filipe");
        } finally {
            Locale.setDefault(original);
        }
    }
}
