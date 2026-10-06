package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.aplicacao.RelatorioService.ResumoDoMes;
import com.gefinx.backend.financas.aplicacao.RelatorioService.TotalDaCategoria;
import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.MovimentoDoMes;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.TotalPorCategoria;
import com.gefinx.backend.financas.dominio.excecoes.RelatorioInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RelatorioServiceTest {

    private static final Long USUARIO = 1L;
    private static final YearMonth JANEIRO = YearMonth.of(2027, 1);
    private static final YearMonth MARCO = YearMonth.of(2027, 3);

    private RepositorioTransacao repositorioTransacao;
    private RepositorioCategoria repositorioCategoria;
    private CalcularSaldoService calcularSaldoService;
    private RelatorioService servico;

    @BeforeEach
    void preparar() {
        repositorioTransacao = mock(RepositorioTransacao.class);
        repositorioCategoria = mock(RepositorioCategoria.class);
        calcularSaldoService = mock(CalcularSaldoService.class);
        servico = new RelatorioService(repositorioTransacao, repositorioCategoria, calcularSaldoService);
    }

    /** Fevereiro sem lançamento aparece com zero, e o saldo atravessa o buraco sem mudar. */
    @Test
    void preencheOsMesesVaziosEAcumulaOSaldo() {
        when(calcularSaldoService.calcular(USUARIO, new Periodo(null, LocalDate.of(2026, 12, 31))))
            .thenReturn(new ResultadoSaldo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000")));
        when(repositorioTransacao.somarPorMes(USUARIO, new Periodo(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31))))
            .thenReturn(List.of(
                new MovimentoDoMes(JANEIRO, new BigDecimal("500"), new BigDecimal("200")),
                new MovimentoDoMes(MARCO, BigDecimal.ZERO, new BigDecimal("1300"))
            ));

        List<ResumoDoMes> meses = servico.mensal(USUARIO, JANEIRO, MARCO);

        assertThat(meses).extracting(ResumoDoMes::mes).containsExactly(JANEIRO, YearMonth.of(2027, 2), MARCO);
        assertThat(meses).extracting(ResumoDoMes::saldoAoFim)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("1300"), new BigDecimal("1300"), BigDecimal.ZERO);
        assertThat(meses.get(1).resultado()).isEqualByComparingTo("0");
        assertThat(meses.get(2).resultado()).isEqualByComparingTo("-1300");
    }

    @Test
    void recusaMesesInvertidosOuDemais() {
        assertThatThrownBy(() -> servico.mensal(USUARIO, MARCO, JANEIRO)).isInstanceOf(RelatorioInvalidoException.class);
        assertThatThrownBy(() -> servico.mensal(USUARIO, JANEIRO, JANEIRO.plusMonths(RelatorioService.MAXIMO_DE_MESES)))
            .isInstanceOf(RelatorioInvalidoException.class);
        verifyNoInteractions(repositorioTransacao, calcularSaldoService);
    }

    @Test
    void vinteEQuatroMesesCabem() {
        when(calcularSaldoService.calcular(eq(USUARIO), any()))
            .thenReturn(new ResultadoSaldo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        assertThat(servico.mensal(USUARIO, JANEIRO, JANEIRO.plusMonths(RelatorioService.MAXIMO_DE_MESES - 1)))
            .hasSize(RelatorioService.MAXIMO_DE_MESES);
    }

    @Test
    void categoriasDoMaiorParaOMenorComONome() {
        when(repositorioCategoria.listarPorUsuario(USUARIO)).thenReturn(List.of(
            new Categoria(7L, "Lazer", TipoTransacao.DESPESA, USUARIO),
            new Categoria(8L, "Mercado", TipoTransacao.DESPESA, USUARIO)
        ));
        when(repositorioTransacao.somarPorCategoria(eq(USUARIO), eq(TipoTransacao.DESPESA), any())).thenReturn(List.of(
            new TotalPorCategoria(7L, new BigDecimal("50")),
            new TotalPorCategoria(8L, new BigDecimal("200"))
        ));

        List<TotalDaCategoria> totais = servico.porCategoria(USUARIO, JANEIRO, TipoTransacao.DESPESA);

        assertThat(totais).extracting(TotalDaCategoria::nome).containsExactly("Mercado", "Lazer");
    }

    @Test
    void transferenciaNaoTemCategoria() {
        assertThatThrownBy(() -> servico.porCategoria(USUARIO, JANEIRO, TipoTransacao.TRANSFERENCIA))
            .isInstanceOf(RelatorioInvalidoException.class);
    }
}
