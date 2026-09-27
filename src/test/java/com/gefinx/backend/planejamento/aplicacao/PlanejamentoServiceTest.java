package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.DespesaLancada;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioContasDeFora;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanejamentoServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long BANCO = 10L;
    private static final Long ESPECIE = 11L;
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);

    private RepositorioDespesaPlanejada repositorioDespesa;
    private RepositorioContasDeFora repositorioContasDeFora;
    private FonteDeSaldo fonteDeSaldo;

    @BeforeEach
    void preparar() {
        repositorioDespesa = mock(RepositorioDespesaPlanejada.class);
        repositorioContasDeFora = mock(RepositorioContasDeFora.class);
        fonteDeSaldo = mock(FonteDeSaldo.class);
        when(fonteDeSaldo.listarContas(USUARIO)).thenReturn(List.of(
            new ContaDoPlanejamento(BANCO, "Banco"), new ContaDoPlanejamento(ESPECIE, "Em espécie")
        ));
    }

    @Test
    void comparaOSaldoDeOntemComODeHojeSemAsContasDeFora() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of(ESPECIE));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO.minusDays(1), Set.of(ESPECIE))).thenReturn(new BigDecimal("1000.00"));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO, Set.of(ESPECIE))).thenReturn(new BigDecimal("1040.00"));
        when(repositorioDespesa.listarPendentesPorUsuario(USUARIO)).thenReturn(List.of(
            new DespesaPlanejada(1L, "Cartão", new BigDecimal("2300.00"), LocalDate.of(2026, 10, 20), USUARIO)
        ));

        PlanoDePagamento plano = servicoEm(Instant.parse("2026-09-26T15:00:00Z")).montar(USUARIO);

        assertThat(plano.hoje()).isEqualTo(SABADO);
        assertThat(plano.ganhoDeHoje()).isEqualByComparingTo("40.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("65.00");
    }

    /**
     * Ontem havia 1000; hoje paguei 500 do aluguel planejado e ganhei 40, e agora há 540. O
     * aluguel saiu da lista. O ganho de hoje é 40, e não -460, e o cartão continua pedindo o
     * que pedia: 2000 - (1000 - 500) = 1500, em 20 dias, R$ 75,00.
     */
    @Test
    void pagarHojeUmaDespesaPlanejadaNaoContaComoPerdaDoDia() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of());
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO.minusDays(1), Set.of())).thenReturn(new BigDecimal("1000.00"));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO, Set.of())).thenReturn(new BigDecimal("540.00"));
        when(fonteDeSaldo.despesasLancadasEm(USUARIO, SABADO)).thenReturn(List.of(
            new DespesaLancada(70L, new BigDecimal("500.00"), BANCO),
            new DespesaLancada(71L, new BigDecimal("30.00"), BANCO)
        ));
        when(repositorioDespesa.filtrarPagamentos(USUARIO, List.of(70L, 71L))).thenReturn(Set.of(70L));
        when(repositorioDespesa.listarPendentesPorUsuario(USUARIO)).thenReturn(List.of(
            new DespesaPlanejada(2L, "Cartão", new BigDecimal("2000.00"), LocalDate.of(2026, 10, 20), USUARIO)
        ));

        PlanoDePagamento plano = servicoEm(Instant.parse("2026-09-26T15:00:00Z")).montar(USUARIO);

        assertThat(plano.saldoDeOntem()).isEqualByComparingTo("500.00");
        assertThat(plano.ganhoDeHoje())
            .as("a despesa de 30 que não era planejada é gasto do dia, e continua contando")
            .isEqualByComparingTo("40.00");
        assertThat(plano.metaDiaria()).isEqualByComparingTo("75.00");
    }

    @Test
    void pagamentoFeitoDeContaDeForaNaoEntraNoAjuste() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of(ESPECIE));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO.minusDays(1), Set.of(ESPECIE))).thenReturn(new BigDecimal("1000.00"));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO, Set.of(ESPECIE))).thenReturn(new BigDecimal("1000.00"));
        when(fonteDeSaldo.despesasLancadasEm(USUARIO, SABADO)).thenReturn(List.of(
            new DespesaLancada(70L, new BigDecimal("500.00"), ESPECIE)
        ));

        PlanoDePagamento plano = servicoEm(Instant.parse("2026-09-26T15:00:00Z")).montar(USUARIO);

        assertThat(plano.saldoDeOntem()).isEqualByComparingTo("1000.00");
        assertThat(plano.ganhoDeHoje()).isEqualByComparingTo("0");
        verify(repositorioDespesa).filtrarPagamentos(USUARIO, List.of());
    }

    /** 23h30 de sábado em São Paulo já é domingo em UTC. O dia do plano é o de quem usa. */
    @Test
    void hojeEODiaNoFusoConfiguradoENaoEmUtc() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of());
        when(fonteDeSaldo.saldoAte(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        PlanoDePagamento plano = servicoEm(Instant.parse("2026-09-27T02:30:00Z")).montar(USUARIO);

        assertThat(plano.hoje()).isEqualTo(SABADO);
    }

    @Test
    void recusaMarcarContaQueNaoEDoUsuario() {
        PlanejamentoService servico = servicoEm(Instant.parse("2026-09-26T15:00:00Z"));

        assertThatThrownBy(() -> servico.definirContasDeFora(USUARIO, Set.of(ESPECIE, 999L)))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(repositorioContasDeFora, never()).substituir(any(), any());
    }

    @Test
    void aceitaListaVaziaQuePoeTodasAsContasNoPlanejamento() {
        servicoEm(Instant.parse("2026-09-26T15:00:00Z")).definirContasDeFora(USUARIO, Set.of());

        verify(repositorioContasDeFora).substituir(USUARIO, Set.of());
    }

    private PlanejamentoService servicoEm(Instant instante) {
        return new PlanejamentoService(
            repositorioDespesa, repositorioContasDeFora, fonteDeSaldo, Clock.fixed(instante, SAO_PAULO)
        );
    }
}
