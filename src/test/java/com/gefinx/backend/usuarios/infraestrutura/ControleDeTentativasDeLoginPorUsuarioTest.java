package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ControleDeTentativasDeLoginPorUsuarioTest {

    private static final int TENTATIVAS = 3;

    private ControleDeTentativasDeLogin controle;

    @BeforeEach
    void preparar() {
        var politica = new Politica(TENTATIVAS, Duration.ofMinutes(15));
        var propriedades = new PropriedadesLimiteDeRequisicoes(politica, politica);
        controle = new ControleDeTentativasDeLoginPorUsuario(new LimitadorDeRequisicoes(), propriedades);
    }

    @Test
    void bloqueiaDepoisDeEsgotarAsTentativas() {
        for (int i = 0; i < TENTATIVAS; i++) {
            assertThat(controle.registrar("alvo").bloqueado()).isFalse();
        }

        var excedente = controle.registrar("alvo");
        assertThat(excedente.bloqueado()).isTrue();
        assertThat(excedente.segundosParaLiberar()).isPositive();
    }

    @Test
    void trataVariacoesDeCaixaEEspacoComoAMesmaConta() {
        controle.registrar("alvo");
        controle.registrar("ALVO");
        controle.registrar("  Alvo  ");

        // As três grafias esgotaram um único balde: sem normalizar a chave, bastaria
        // alternar a caixa para tentar à vontade.
        assertThat(controle.registrar("alvo").bloqueado()).isTrue();
    }

    @Test
    void mantemContasDiferentesEmBaldesSeparados() {
        for (int i = 0; i < TENTATIVAS + 1; i++) {
            controle.registrar("alvo");
        }

        assertThat(controle.registrar("outro").bloqueado()).isFalse();
    }

    @Test
    void liberarDevolveACotaCheia() {
        for (int i = 0; i < TENTATIVAS; i++) {
            controle.registrar("alvo");
        }
        assertThat(controle.registrar("alvo").bloqueado()).isTrue();

        controle.liberar("ALVO");

        assertThat(controle.registrar("alvo").bloqueado()).isFalse();
    }
}
