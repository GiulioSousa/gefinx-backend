package com.gefinx.backend.usuarios.interfaces.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A canonização acontece na construção do DTO — depois da desserialização e antes da
 * validação. Feita só no caso de uso, um nome colado com espaço ao final chegaria com
 * conteúdo e ainda assim precisaria ser comparado duas vezes; feita aqui, o que a
 * validação vê já é a forma que o banco guarda.
 */
class RequisicaoLoginTest {

    @Test
    void canonizaONomeDeUsuarioNaConstrucao() {
        var requisicao = new RequisicaoLogin("  Fulano  ", "senha");

        assertThat(requisicao.usuario()).isEqualTo("fulano");
    }

    @Test
    void deixaNomeNuloParaAValidacaoDeObrigatoriedade() {
        assertThat(new RequisicaoLogin(null, "senha").usuario()).isNull();
    }
}
