package com.gefinx.backend.financas.dominio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A aritmética de páginas, sem contexto nem banco. É a conta que decide quantos botões a
 * navegação desenha, e a que erra por um quando alguém a escreve como divisão inteira.
 */
class PaginaTest {

    @Test
    void aSobraDaDivisaoAindaOcupaUmaPaginaInteira() {
        Pagina<String> pagina = new Pagina<>(List.of("a", "b"), 0, 2, 5);

        assertThat(pagina.totalPaginas())
            .as("5 itens de 2 em 2 são três páginas, e não duas: a última leva o item que sobrou")
            .isEqualTo(3);
    }

    @Test
    void divisaoExataNaoInventaUmaPaginaVazia() {
        assertThat(new Pagina<>(List.of("a"), 0, 2, 4).totalPaginas()).isEqualTo(2);
    }

    @Test
    void resultadoVazioNaoTemPaginaAlguma() {
        assertThat(new Pagina<>(List.of(), 0, 20, 0).totalPaginas()).isZero();
    }

    @Test
    void tamanhoInvalidoNaoEstouraAoDividir() {
        assertThat(new Pagina<>(List.of(), 0, 0, 10).totalPaginas())
            .as("a borda recusa tamanho zero, mas o domínio não pode depender disso para não dividir por zero")
            .isZero();
    }
}
