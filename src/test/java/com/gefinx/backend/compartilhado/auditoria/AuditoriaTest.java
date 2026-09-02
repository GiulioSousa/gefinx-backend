package com.gefinx.backend.compartilhado.auditoria;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditoriaTest {

    @Test
    void trocaQuebrasDeLinhaParaQueUmValorNaoForjeUmEvento() {
        String forjado = "alvo\nlogin aceito id=1 usuario=vitima";

        String resultado = Auditoria.seguro(forjado);

        // Uma linha por evento: se a quebra passasse, a segunda linha entraria na trilha
        // como um evento que nunca aconteceu.
        assertThat(resultado).doesNotContain("\n").doesNotContain("\r");
        assertThat(resultado).startsWith("alvo_login aceito");
    }

    @Test
    void trocaTambemORetornoDeCarroSozinho() {
        assertThat(Auditoria.seguro("a\rb")).isEqualTo("a_b");
    }

    @Test
    void truncaValorAcimaDoTetoDeTexto() {
        String longo = "a".repeat(500);

        String resultado = Auditoria.seguro(longo);

        assertThat(resultado).hasSize(123).endsWith("...");
    }

    @Test
    void representaValorAusenteOuVazioComTraco() {
        assertThat(Auditoria.seguro(null)).isEqualTo("-");
        assertThat(Auditoria.seguro("")).isEqualTo("-");
    }

    @Test
    void mantemIntactoOValorComumDeUmEvento() {
        assertThat(Auditoria.seguro("alvo")).isEqualTo("alvo");
    }
}
