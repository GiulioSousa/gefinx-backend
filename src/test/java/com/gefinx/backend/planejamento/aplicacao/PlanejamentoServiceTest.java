package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
import com.gefinx.backend.planejamento.dominio.CategoriaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.Lancamento;
import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioCategoriasDoCustoDiario;
import com.gefinx.backend.planejamento.dominio.RepositorioContasDeFora;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RepositorioEtiquetas;
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
    private static final Long ALIMENTACAO = 20L;
    private static final Long LAZER = 21L;
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate SABADO = LocalDate.of(2026, 9, 26);
    private static final LocalDate SEXTA = SABADO.minusDays(1);
    private static final Instant SABADO_AS_15H = Instant.parse("2026-09-26T15:00:00Z");

    private RepositorioDespesaPlanejada repositorioDespesa;
    private RepositorioContasDeFora repositorioContasDeFora;
    private RepositorioCategoriasDoCustoDiario repositorioCategorias;
    private FonteDeSaldo fonteDeSaldo;

    @BeforeEach
    void preparar() {
        repositorioDespesa = mock(RepositorioDespesaPlanejada.class);
        repositorioContasDeFora = mock(RepositorioContasDeFora.class);
        repositorioCategorias = mock(RepositorioCategoriasDoCustoDiario.class);
        fonteDeSaldo = mock(FonteDeSaldo.class);
        when(fonteDeSaldo.listarContas(USUARIO)).thenReturn(List.of(
            new ContaDoPlanejamento(BANCO, "Banco"), new ContaDoPlanejamento(ESPECIE, "Em espécie")
        ));
        when(fonteDeSaldo.listarCategoriasDeDespesa(USUARIO)).thenReturn(List.of(
            new CategoriaDoPlanejamento(ALIMENTACAO, "Alimentação"), new CategoriaDoPlanejamento(LAZER, "Lazer")
        ));
    }

    @Test
    void usaOSaldoDeAgoraSemAsContasDeFora() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of(ESPECIE));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO, Set.of(ESPECIE))).thenReturn(new BigDecimal("1040.00"));
        when(repositorioDespesa.listarPendentesPorUsuario(USUARIO)).thenReturn(List.of(
            new DespesaPlanejada(1L, "Cartão", new BigDecimal("2300.00"), LocalDate.of(2026, 10, 20), USUARIO)
        ));

        PlanoDePagamento plano = servicoEm(SABADO_AS_15H).montar(USUARIO);

        assertThat(plano.hoje()).isEqualTo(SABADO);
        // (2300 - 1040) / 20
        assertThat(plano.metaDiaria()).isEqualByComparingTo("63.00");
    }

    @Test
    void asReceitasDeHojeSaoSomadasSemAsDasContasDeFora() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of(ESPECIE));
        when(fonteDeSaldo.saldoAte(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(fonteDeSaldo.receitasLancadas(USUARIO, SABADO, SABADO)).thenReturn(List.of(
            lancamento(90L, "40.00", SABADO, BANCO, null),
            lancamento(91L, "60.00", SABADO, BANCO, null),
            lancamento(92L, "25.00", SABADO, ESPECIE, null)
        ));

        PlanoDePagamento plano = servicoEm(SABADO_AS_15H).montar(USUARIO);

        assertThat(plano.receitasDeHoje()).isEqualByComparingTo("100.00");
    }

    /**
     * Entram a de 100 e a de 20, de sexta, e a de 60, de quinta: 180 em dois dias com gasto, 90
     * por dia. A de 50 saiu da carteira, que está fora; a de 300 é o pagamento de uma planejada;
     * a de 70 é de outra categoria. A de 30, de hoje, não entra na média, e volta para o saldo
     * da meta.
     */
    @Test
    void oCustoDiarioEAMediaDasCategoriasEscolhidasPelosDiasComGasto() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of(ESPECIE));
        when(repositorioCategorias.listarPorUsuario(USUARIO)).thenReturn(Set.of(ALIMENTACAO));
        when(fonteDeSaldo.saldoAte(USUARIO, SABADO, Set.of(ESPECIE))).thenReturn(new BigDecimal("970.00"));
        when(fonteDeSaldo.despesasLancadas(USUARIO, SABADO.minusDays(30), SABADO)).thenReturn(List.of(
            lancamento(80L, "100.00", SEXTA, BANCO, ALIMENTACAO),
            lancamento(81L, "50.00", SEXTA, ESPECIE, ALIMENTACAO),
            lancamento(82L, "300.00", SEXTA, BANCO, ALIMENTACAO),
            lancamento(83L, "70.00", SEXTA, BANCO, LAZER),
            lancamento(84L, "30.00", SABADO, BANCO, ALIMENTACAO),
            lancamento(85L, "20.00", SEXTA, BANCO, ALIMENTACAO),
            lancamento(86L, "60.00", SEXTA.minusDays(1), BANCO, ALIMENTACAO)
        ));
        when(repositorioDespesa.filtrarPagamentos(USUARIO, List.of(80L, 82L, 84L, 85L, 86L))).thenReturn(Set.of(82L));
        when(repositorioDespesa.listarPendentesPorUsuario(USUARIO)).thenReturn(List.of(
            new DespesaPlanejada(1L, "Cartão", new BigDecimal("2300.00"), LocalDate.of(2026, 10, 20), USUARIO)
        ));

        PlanoDePagamento plano = servicoEm(SABADO_AS_15H).montar(USUARIO);

        assertThat(plano.custoDiario().total()).isEqualByComparingTo("180.00");
        assertThat(plano.custoDiario().categorias().get(0).diasComGasto()).isEqualTo(2);
        assertThat(plano.custoDiario().porDia()).isEqualByComparingTo("90.00");
        assertThat(plano.itens().get(0).falta())
            .as("a falta é dinheiro de verdade, e o gasto de hoje saiu dele")
            .isEqualByComparingTo("1330.00");
        // (2300 - 1000) / 20 = 65, com os 30 de hoje devolvidos, mais 90 do custo.
        assertThat(plano.metaDiaria()).isEqualByComparingTo("155.00");
    }

    @Test
    void semCategoriaEscolhidaNaoHaCustoDiarioNemLeitura() {
        when(repositorioContasDeFora.listarPorUsuario(USUARIO)).thenReturn(Set.of());
        when(fonteDeSaldo.saldoAte(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        PlanoDePagamento plano = servicoEm(SABADO_AS_15H).montar(USUARIO);

        assertThat(plano.custoDiario()).isNull();
        verify(fonteDeSaldo, never()).despesasLancadas(anyLong(), any(), any());
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
        PlanejamentoService servico = servicoEm(SABADO_AS_15H);

        assertThatThrownBy(() -> servico.definirContasDeFora(USUARIO, Set.of(ESPECIE, 999L)))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(repositorioContasDeFora, never()).substituir(any(), any());
    }

    @Test
    void aceitaListaVaziaQuePoeTodasAsContasNoPlanejamento() {
        servicoEm(SABADO_AS_15H).definirContasDeFora(USUARIO, Set.of());

        verify(repositorioContasDeFora).substituir(USUARIO, Set.of());
    }

    /** 999 pode ser de outro usuário ou de receita: nos dois casos não está na lista de despesas dele. */
    @Test
    void recusaCategoriaQueNaoEDeDespesaDoUsuario() {
        PlanejamentoService servico = servicoEm(SABADO_AS_15H);

        assertThatThrownBy(() -> servico.definirCategoriasDoCustoDiario(USUARIO, Set.of(ALIMENTACAO, 999L)))
            .isInstanceOf(RecursoNaoEncontradoNoPlanejamentoException.class);
        verify(repositorioCategorias, never()).substituir(any(), any());
    }

    @Test
    void guardaAsCategoriasEscolhidas() {
        servicoEm(SABADO_AS_15H).definirCategoriasDoCustoDiario(USUARIO, Set.of(ALIMENTACAO));

        verify(repositorioCategorias).substituir(USUARIO, Set.of(ALIMENTACAO));
    }

    private static Lancamento lancamento(Long id, String valor, LocalDate data, Long conta, Long categoria) {
        return new Lancamento(id, new BigDecimal(valor), data, conta, categoria);
    }

    private PlanejamentoService servicoEm(Instant instante) {
        Clock relogio = Clock.fixed(instante, SAO_PAULO);
        DespesaPlanejadaService despesas = new DespesaPlanejadaService(
            repositorioDespesa, mock(RepositorioEtiquetas.class), mock(RegistroDePagamento.class), fonteDeSaldo, relogio
        );
        // Sem feriados: o 12/10 cai entre os exemplos, e o que se testa aqui é o saldo, não o
        // calendário, que tem os próprios testes.
        CalendarioService calendario = mock(CalendarioService.class);
        when(calendario.doUsuario(USUARIO)).thenReturn(CalendarioDeTrabalho.SEGUNDA_A_SABADO);
        return new PlanejamentoService(
            repositorioDespesa, despesas, repositorioContasDeFora, repositorioCategorias, fonteDeSaldo, calendario,
            relogio
        );
    }
}
