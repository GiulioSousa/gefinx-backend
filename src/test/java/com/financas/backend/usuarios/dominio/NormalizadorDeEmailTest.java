package com.financas.backend.usuarios.dominio;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorDeEmailTest {

    @Test
    void reduzMaiusculasAMinusculas() {
        assertThat(NormalizadorDeEmail.normalizar("TESTE@Exemplo.COM")).isEqualTo("teste@exemplo.com");
    }

    @Test
    void removeEspacosNasPontas() {
        assertThat(NormalizadorDeEmail.normalizar("  teste@exemplo.com  ")).isEqualTo("teste@exemplo.com");
    }

    @Test
    void mantemNuloComoNulo() {
        assertThat(NormalizadorDeEmail.normalizar(null)).isNull();
    }

    @Test
    void naoAlteraEmailJaCanonico() {
        assertThat(NormalizadorDeEmail.normalizar("teste@exemplo.com")).isEqualTo("teste@exemplo.com");
    }

    /**
     * Em turco o minúsculo de {@code I} é {@code ı}, sem pingo. Com o locale da máquina,
     * o mesmo endereço normalizaria diferente conforme onde a aplicação roda, e duas
     * instâncias discordariam sobre qual conta é qual.
     */
    @Test
    void independeDoLocaleDaMaquina() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(NormalizadorDeEmail.normalizar("FILIPE@EXEMPLO.COM")).isEqualTo("filipe@exemplo.com");
        } finally {
            Locale.setDefault(original);
        }
    }
}
