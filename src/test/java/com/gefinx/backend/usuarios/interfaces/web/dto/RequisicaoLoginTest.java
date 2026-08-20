package com.gefinx.backend.usuarios.interfaces.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O {@code @Email} recusa espaços nas pontas, então a canonização precisa acontecer na
 * construção do DTO — depois da desserialização e antes da validação. Feita apenas no
 * caso de uso, um endereço colado com espaço morreria como "E-mail inválido".
 */
class RequisicaoLoginTest {

    @Test
    void canonizaOEmailNaConstrucao() {
        var requisicao = new RequisicaoLogin("  TESTE@Exemplo.COM  ", "senha");

        assertThat(requisicao.email()).isEqualTo("teste@exemplo.com");
    }

    @Test
    void deixaEmailNuloParaAValidacaoDeObrigatoriedade() {
        assertThat(new RequisicaoLogin(null, "senha").email()).isNull();
    }

    @Test
    void canonizaTambemNoCadastro() {
        var requisicao = new RequisicaoRegistro("Fulano", " Fulano@Exemplo.COM ", "uma frase de senha");

        assertThat(requisicao.email()).isEqualTo("fulano@exemplo.com");
    }
}
