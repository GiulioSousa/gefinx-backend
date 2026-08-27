package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalcularSaldoServiceTest {

    private static final Long USUARIO = 1L;
    private static final Long CONTA = 20L;

    private RepositorioTransacao repositorioTransacao;
    private RepositorioConta repositorioConta;
    private CalcularSaldoService servico;

    @BeforeEach
    void preparar() {
        repositorioTransacao = mock(RepositorioTransacao.class);
        repositorioConta = mock(RepositorioConta.class);
        servico = new CalcularSaldoService(repositorioTransacao, repositorioConta);
    }

    @Test
    void oConsolidadoSomaTodasAsContasDoUsuario() {
        when(repositorioTransacao.somarValorPorUsuarioETipo(USUARIO, TipoTransacao.RECEITA))
            .thenReturn(new BigDecimal("5000.00"));
        when(repositorioTransacao.somarValorPorUsuarioETipo(USUARIO, TipoTransacao.DESPESA))
            .thenReturn(new BigDecimal("571.25"));

        ResultadoSaldo resultado = servico.calcular(USUARIO);

        assertThat(resultado.totalReceitas()).isEqualByComparingTo("5000.00");
        assertThat(resultado.totalDespesas()).isEqualByComparingTo("571.25");
        assertThat(resultado.saldo()).isEqualByComparingTo("4428.75");
    }

    @Test
    void aLeituraPorContaSomaSoAquelaConta() {
        contaDoUsuario();
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.RECEITA))
            .thenReturn(new BigDecimal("340.00"));
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.DESPESA))
            .thenReturn(new BigDecimal("5.54"));

        assertThat(servico.calcularPorConta(USUARIO, CONTA).saldo()).isEqualByComparingTo("334.46");
    }

    /**
     * O id vem da URL, e o do usuário do SecurityContext. Sem conferir a dupla, um id
     * chutado leria o saldo da conta de outra pessoa — o mesmo IDOR que todo o resto da
     * API evita buscando sempre por (id, usuário).
     */
    @Test
    void recusaLerOSaldoDeContaDeOutroUsuario() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.calcularPorConta(USUARIO, CONTA))
            .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoSomaNadaQuandoAContaNaoEDoUsuario() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.calcularPorConta(USUARIO, CONTA))
            .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(repositorioTransacao, never()).somarValorPorContaETipo(any(), any());
    }

    /**
     * O zero de transferências é o padrão porque a maioria dos casos não tem nenhuma; os
     * testes que se importam com elas re-especificam o valor. Sem este stub o mock
     * devolveria nulo, coisa que a consulta real nunca faz — ela vem com {@code COALESCE}.
     */
    private void contaDoUsuario() {
        when(repositorioConta.buscarPorIdEUsuario(CONTA, USUARIO))
            .thenReturn(Optional.of(new Conta(CONTA, "Conta principal", USUARIO)));
        when(repositorioTransacao.somarTransferenciasLiquidasDaConta(CONTA)).thenReturn(BigDecimal.ZERO);
    }

    /**
     * O ponto da Etapa 20: dinheiro que entrou por transferência não é receita. Somá-lo
     * em totalReceitas faria o extrato da conta afirmar uma renda que nunca existiu.
     */
    @Test
    void aTransferenciaRecebidaNaoContaComoReceita() {
        contaDoUsuario();
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.RECEITA))
            .thenReturn(BigDecimal.ZERO);
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.DESPESA))
            .thenReturn(BigDecimal.ZERO);
        when(repositorioTransacao.somarTransferenciasLiquidasDaConta(CONTA))
            .thenReturn(new BigDecimal("200.00"));

        ResultadoSaldo resultado = servico.calcularPorConta(USUARIO, CONTA);

        assertThat(resultado.totalReceitas()).isEqualByComparingTo("0");
        assertThat(resultado.totalTransferencias()).isEqualByComparingTo("200.00");
        assertThat(resultado.saldo()).isEqualByComparingTo("200.00");
    }

    @Test
    void aTransferenciaEnviadaEntraNegativaNaContaDeOrigem() {
        contaDoUsuario();
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.RECEITA))
            .thenReturn(new BigDecimal("1000.00"));
        when(repositorioTransacao.somarValorPorContaETipo(CONTA, TipoTransacao.DESPESA))
            .thenReturn(BigDecimal.ZERO);
        when(repositorioTransacao.somarTransferenciasLiquidasDaConta(CONTA))
            .thenReturn(new BigDecimal("-200.00"));

        ResultadoSaldo resultado = servico.calcularPorConta(USUARIO, CONTA);

        assertThat(resultado.totalDespesas()).isEqualByComparingTo("0");
        assertThat(resultado.saldo()).isEqualByComparingTo("800.00");
    }

    /**
     * No consolidado o líquido é zero por construção — as duas pontas de uma
     * transferência pertencem ao mesmo usuário, garantido pela chave estrangeira composta
     * da V8. Nem consulta o repositório para descobrir isso.
     */
    @Test
    void oConsolidadoIgnoraTransferenciasSemConsultarORepositorio() {
        when(repositorioTransacao.somarValorPorUsuarioETipo(USUARIO, TipoTransacao.RECEITA))
            .thenReturn(new BigDecimal("5000.00"));
        when(repositorioTransacao.somarValorPorUsuarioETipo(USUARIO, TipoTransacao.DESPESA))
            .thenReturn(new BigDecimal("571.25"));

        ResultadoSaldo resultado = servico.calcular(USUARIO);

        assertThat(resultado.totalTransferencias()).isEqualByComparingTo("0");
        assertThat(resultado.saldo()).isEqualByComparingTo("4428.75");
        verify(repositorioTransacao, never()).somarTransferenciasLiquidasDaConta(any());
    }
}
