package com.gefinx.backend.planejamento.dominio;

import com.gefinx.backend.planejamento.dominio.CustoDiario.MediaDaCategoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustoDiarioTest {

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);
    private static final Long ALIMENTACAO = 20L;
    private static final Long COMBUSTIVEL = 21L;

    @Test
    void aJanelaSaoOsTrintaDiasAteOntem() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(), List.of());

        assertThat(custo.inicio()).isEqualTo(LocalDate.of(2026, 8, 29));
        assertThat(custo.fim()).isEqualTo(LocalDate.of(2026, 9, 27));
    }

    /** O exemplo do dono: combustível em 15 dos 30 dias soma os 15 e divide por 15, e não por 30. */
    @Test
    void divideCadaCategoriaPelosDiasEmQueHouveGasto() {
        List<Lancamento> gastos = new ArrayList<>();
        for (int dia = 1; dia <= 30; dia += 2) {
            gastos.add(gasto(COMBUSTIVEL, "40.00", SEGUNDA.minusDays(dia)));
        }

        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(COMBUSTIVEL), gastos);

        MediaDaCategoria combustivel = custo.categorias().get(0);
        assertThat(combustivel.total()).isEqualByComparingTo("600.00");
        assertThat(combustivel.diasComGasto()).isEqualTo(15);
        assertThat(combustivel.porDia()).isEqualByComparingTo("40.00");
    }

    /** Dois gastos no mesmo dia são um dia com gasto, e não dois. */
    @Test
    void doisGastosNoMesmoDiaContamUmDia() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(ALIMENTACAO), List.of(
            gasto(ALIMENTACAO, "20.00", SEGUNDA.minusDays(1)),
            gasto(ALIMENTACAO, "30.00", SEGUNDA.minusDays(1)),
            gasto(ALIMENTACAO, "40.00", SEGUNDA.minusDays(4))
        ));

        assertThat(custo.categorias().get(0).diasComGasto()).isEqualTo(2);
        assertThat(custo.porDia()).isEqualByComparingTo("45.00");
    }

    /** Cada categoria tem os seus dias; o custo do dia é a soma das médias. */
    @Test
    void oCustoDoDiaEASomaDasMediasDasCategorias() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(ALIMENTACAO, COMBUSTIVEL), List.of(
            gasto(ALIMENTACAO, "25.00", SEGUNDA.minusDays(1)),
            gasto(ALIMENTACAO, "35.00", SEGUNDA.minusDays(2)),
            gasto(COMBUSTIVEL, "100.00", SEGUNDA.minusDays(3))
        ));

        assertThat(custo.categorias()).extracting(MediaDaCategoria::categoriaId)
            .as("da maior média para a menor")
            .containsExactly(COMBUSTIVEL, ALIMENTACAO);
        assertThat(custo.total()).isEqualByComparingTo("160.00");
        assertThat(custo.porDia()).isEqualByComparingTo("130.00");
    }

    @Test
    void arredondaParaCimaNoCentavo() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(ALIMENTACAO), List.of(
            gasto(ALIMENTACAO, "10.00", SEGUNDA.minusDays(1)),
            gasto(ALIMENTACAO, "10.00", SEGUNDA.minusDays(2)),
            gasto(ALIMENTACAO, "10.01", SEGUNDA.minusDays(3))
        ));

        assertThat(custo.porDia()).isEqualByComparingTo("10.01");
    }

    @Test
    void categoriaSemGastoNaJanelaFicaComZero() {
        CustoDiario custo = CustoDiario.calcular(SEGUNDA, Set.of(ALIMENTACAO), List.of());

        MediaDaCategoria alimentacao = custo.categorias().get(0);
        assertThat(alimentacao.diasComGasto()).isZero();
        assertThat(alimentacao.porDia()).isEqualByComparingTo("0");
        assertThat(custo.porDia()).isEqualByComparingTo("0");
    }

    private static Lancamento gasto(Long categoria, String valor, LocalDate data) {
        return new Lancamento(null, new BigDecimal(valor), data, 1L, categoria);
    }
}
