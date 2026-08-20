package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.financas.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.financas.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import com.financas.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ControleDeTentativasDeLoginPorEmailTest {

    private static final int TENTATIVAS = 3;

    private ControleDeTentativasDeLogin controle;

    @BeforeEach
    void preparar() {
        var politica = new Politica(TENTATIVAS, Duration.ofMinutes(15));
        var propriedades = new PropriedadesLimiteDeRequisicoes(politica, politica, politica);
        controle = new ControleDeTentativasDeLoginPorEmail(new LimitadorDeRequisicoes(), propriedades);
    }

    @Test
    void bloqueiaDepoisDeEsgotarAsTentativas() {
        for (int i = 0; i < TENTATIVAS; i++) {
            assertThat(controle.registrar("alvo@exemplo.com").bloqueado()).isFalse();
        }

        var excedente = controle.registrar("alvo@exemplo.com");
        assertThat(excedente.bloqueado()).isTrue();
        assertThat(excedente.segundosParaLiberar()).isPositive();
    }

    @Test
    void trataVariacoesDeCaixaEEspacoComoAMesmaConta() {
        controle.registrar("alvo@exemplo.com");
        controle.registrar("ALVO@exemplo.com");
        controle.registrar("  Alvo@Exemplo.com  ");

        // As três grafias esgotaram um único balde: sem normalizar a chave, bastaria
        // alternar a caixa para tentar à vontade.
        assertThat(controle.registrar("alvo@exemplo.com").bloqueado()).isTrue();
    }

    @Test
    void mantemContasDiferentesEmBaldesSeparados() {
        for (int i = 0; i < TENTATIVAS + 1; i++) {
            controle.registrar("alvo@exemplo.com");
        }

        assertThat(controle.registrar("outro@exemplo.com").bloqueado()).isFalse();
    }

    @Test
    void liberarDevolveACotaCheia() {
        for (int i = 0; i < TENTATIVAS; i++) {
            controle.registrar("alvo@exemplo.com");
        }
        assertThat(controle.registrar("alvo@exemplo.com").bloqueado()).isTrue();

        controle.liberar("ALVO@exemplo.com");

        assertThat(controle.registrar("alvo@exemplo.com").bloqueado()).isFalse();
    }
}
