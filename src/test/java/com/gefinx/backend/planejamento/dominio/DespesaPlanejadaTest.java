package com.gefinx.backend.planejamento.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DespesaPlanejadaTest {

    private static final Long USUARIO = 1L;
    private static final BigDecimal VALOR = new BigDecimal("150.00");

    @Test
    void oDiaDoPrazoViraAAncoraDoMensal() {
        DespesaPlanejada mensal = DespesaPlanejada.nova("Luz", VALOR, LocalDate.of(2027, 1, 31), USUARIO, Recorrencia.MENSAL);
        DespesaPlanejada semanal = DespesaPlanejada.nova("Carro", VALOR, LocalDate.of(2027, 1, 31), USUARIO, Recorrencia.SEMANAL);

        assertThat(mensal.getDiaDoVencimento()).isEqualTo(31);
        assertThat(semanal.getDiaDoVencimento()).isNull();
    }

    @Test
    void aProximaHerdaTudoMenosOPagamentoEApontaParaAAnterior() {
        DespesaPlanejada atual = new DespesaPlanejada(
            7L, "Luz", VALOR, LocalDate.of(2027, 2, 28), USUARIO, 99L, Recorrencia.MENSAL, 31, 6L
        );

        DespesaPlanejada proxima = atual.proxima();

        assertThat(proxima.getId()).isNull();
        assertThat(proxima.getPrazo()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(proxima.getValor()).isEqualByComparingTo(VALOR);
        assertThat(proxima.getRecorrencia()).isEqualTo(Recorrencia.MENSAL);
        assertThat(proxima.getDiaDoVencimento()).isEqualTo(31);
        assertThat(proxima.getAnteriorId()).isEqualTo(7L);
        assertThat(proxima.estaPaga()).isFalse();
    }

    @Test
    void despesaQueNaoSeRepeteNaoTemProxima() {
        DespesaPlanejada avulsa = DespesaPlanejada.nova("IPVA", VALOR, LocalDate.of(2027, 1, 31), USUARIO, null);

        assertThatThrownBy(avulsa::proxima).isInstanceOf(IllegalStateException.class);
    }

    /** Editar o valor da ocorrência de fevereiro não pode trocar o 31 da série pelo 28. */
    @Test
    void editarSemMudarOPrazoMantemAAncora() {
        DespesaPlanejada fevereiro = new DespesaPlanejada(
            7L, "Luz", VALOR, LocalDate.of(2027, 2, 28), USUARIO, null, Recorrencia.MENSAL, 31, 6L
        );

        DespesaPlanejada soOValor = fevereiro.editada("Luz", new BigDecimal("180.00"), LocalDate.of(2027, 2, 28), Recorrencia.MENSAL);
        DespesaPlanejada outroDia = fevereiro.editada("Luz", VALOR, LocalDate.of(2027, 2, 10), Recorrencia.MENSAL);

        assertThat(soOValor.getDiaDoVencimento()).isEqualTo(31);
        assertThat(soOValor.getAnteriorId()).isEqualTo(6L);
        assertThat(outroDia.getDiaDoVencimento()).isEqualTo(10);
    }

    @Test
    void reconheceAProximaComoNasceuEAEditada() {
        DespesaPlanejada atual = new DespesaPlanejada(
            7L, "Luz", VALOR, LocalDate.of(2027, 1, 31), USUARIO, 99L, Recorrencia.MENSAL, 31, null
        );
        DespesaPlanejada comoNasceu = atual.proxima();

        assertThat(atual.gerouSemMudancas(comoNasceu)).isTrue();
        assertThat(atual.gerouSemMudancas(
            comoNasceu.editada("Luz", new BigDecimal("150"), comoNasceu.getPrazo(), Recorrencia.MENSAL)
        )).as("o mesmo valor com outra escala não é edição").isTrue();
        assertThat(atual.gerouSemMudancas(
            comoNasceu.editada("Luz", new BigDecimal("180.00"), comoNasceu.getPrazo(), Recorrencia.MENSAL)
        )).isFalse();
        assertThat(atual.gerouSemMudancas(
            comoNasceu.editada("Luz", VALOR, comoNasceu.getPrazo(), null)
        )).as("a série foi encerrada nela").isFalse();
    }

    @Test
    void tirarARecorrenciaTiraAAncora() {
        DespesaPlanejada mensal = DespesaPlanejada.nova("Luz", VALOR, LocalDate.of(2027, 1, 31), USUARIO, Recorrencia.MENSAL);

        DespesaPlanejada avulsa = mensal.editada("Luz", VALOR, LocalDate.of(2027, 1, 31), null);

        assertThat(avulsa.seRepete()).isFalse();
        assertThat(avulsa.getDiaDoVencimento()).isNull();
    }

    /** O aluguel semanal passou de quinta para sexta: as outras andam o mesmo dia. */
    @Test
    void naSemanalOPrazoMudadoDeslocaAsOutrasPelosMesmosDias() {
        DespesaPlanejada antes = new DespesaPlanejada(
            7L, "Carro", VALOR, LocalDate.of(2026, 10, 15), USUARIO, null, Recorrencia.SEMANAL, null, 6L
        );
        DespesaPlanejada depois = antes.editada("Carro novo", new BigDecimal("650.00"), LocalDate.of(2026, 10, 16), Recorrencia.SEMANAL);
        DespesaPlanejada outra = new DespesaPlanejada(
            8L, "Carro", VALOR, LocalDate.of(2026, 10, 22), USUARIO, null, Recorrencia.SEMANAL, null, 7L
        );

        DespesaPlanejada ajustada = outra.editadaComo(antes, depois);

        assertThat(ajustada.getId()).isEqualTo(8L);
        assertThat(ajustada.getAnteriorId()).isEqualTo(7L);
        assertThat(ajustada.getDescricao()).isEqualTo("Carro novo");
        assertThat(ajustada.getValor()).isEqualByComparingTo("650.00");
        assertThat(ajustada.getPrazo()).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    /** O vencimento mensal passou para o dia 31: novembro cai no 30, e a âncora é o 31. */
    @Test
    void naMensalOPrazoMudadoLevaAsOutrasAoNovoDia() {
        DespesaPlanejada antes = new DespesaPlanejada(
            7L, "Luz", VALOR, LocalDate.of(2026, 10, 10), USUARIO, null, Recorrencia.MENSAL, 10, null
        );
        DespesaPlanejada depois = antes.editada("Luz", VALOR, LocalDate.of(2026, 10, 31), Recorrencia.MENSAL);
        DespesaPlanejada novembro = new DespesaPlanejada(
            8L, "Luz", VALOR, LocalDate.of(2026, 11, 10), USUARIO, null, Recorrencia.MENSAL, 10, 7L
        );

        DespesaPlanejada ajustada = novembro.editadaComo(antes, depois);

        assertThat(ajustada.getPrazo()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(ajustada.getDiaDoVencimento()).isEqualTo(31);
    }

    @Test
    void semMudarOPrazoAsOutrasFicamNoDelas() {
        DespesaPlanejada antes = new DespesaPlanejada(
            7L, "Luz", VALOR, LocalDate.of(2026, 10, 10), USUARIO, null, Recorrencia.MENSAL, 10, null
        );
        DespesaPlanejada depois = antes.editada("Luz", new BigDecimal("180.00"), LocalDate.of(2026, 10, 10), Recorrencia.MENSAL);
        DespesaPlanejada novembro = new DespesaPlanejada(
            8L, "Luz", VALOR, LocalDate.of(2026, 11, 10), USUARIO, null, Recorrencia.MENSAL, 10, 7L
        );

        DespesaPlanejada ajustada = novembro.editadaComo(antes, depois);

        assertThat(ajustada.getPrazo()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(ajustada.getValor()).isEqualByComparingTo("180.00");
    }
}
