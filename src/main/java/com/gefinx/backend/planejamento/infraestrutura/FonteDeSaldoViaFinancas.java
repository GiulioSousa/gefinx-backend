package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.financas.aplicacao.CalcularSaldoService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.financas.aplicacao.TransacaoService;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.Pagina;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.DespesaLancada;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A ponte de leitura com finanças. Ela e {@link RegistroDePagamentoViaFinancas}, a de escrita,
 * são os únicos lugares do planejamento que conhecem classes de lá. Chama os serviços de
 * aplicação, e não os repositórios: o saldo tem regras — transferência fora do consolidado,
 * dentro do saldo por conta — que só os serviços sabem aplicar.
 */
@Component
public class FonteDeSaldoViaFinancas implements FonteDeSaldo {

    /** O teto da listagem de transações. */
    private static final int TAMANHO_DA_PAGINA = 100;

    private final CalcularSaldoService calcularSaldoService;
    private final ContaService contaService;
    private final TransacaoService transacaoService;

    public FonteDeSaldoViaFinancas(
        CalcularSaldoService calcularSaldoService,
        ContaService contaService,
        TransacaoService transacaoService
    ) {
        this.calcularSaldoService = calcularSaldoService;
        this.contaService = contaService;
        this.transacaoService = transacaoService;
    }

    /**
     * O consolidado menos o saldo de cada conta de fora. A subtração acerta com
     * transferência: o consolidado não a enxerga, e o saldo por conta sim — então dinheiro
     * levado do banco para a carteira sai do planejamento, que é o que se quer de uma conta
     * de fora.
     */
    @Override
    public BigDecimal saldoAte(Long usuarioId, LocalDate dia, Set<Long> contasDeFora) {
        Periodo ateODia = new Periodo(null, dia);
        BigDecimal saldo = calcularSaldoService.calcular(usuarioId, ateODia).saldo();
        for (Long contaId : contasDeFora) {
            saldo = saldo.subtract(calcularSaldoService.calcularPorConta(usuarioId, contaId, ateODia).saldo());
        }
        return saldo;
    }

    @Override
    public List<ContaDoPlanejamento> listarContas(Long usuarioId) {
        return contaService.listar(usuarioId).stream()
            .map(conta -> new ContaDoPlanejamento(conta.getId(), conta.getNome()))
            .toList();
    }

    /**
     * Pela mesma listagem paginada da tela de transações, página a página até o fim. As
     * despesas de um dia são poucas, e reaproveitar a listagem poupa finanças de ganhar uma
     * consulta só para o planejamento.
     */
    @Override
    public List<DespesaLancada> despesasLancadasEm(Long usuarioId, LocalDate dia) {
        FiltroDeTransacoes doDia = new FiltroDeTransacoes(dia, dia, TipoTransacao.DESPESA, null, null);
        List<DespesaLancada> despesas = new ArrayList<>();
        Pagina<Transacao> pagina;
        int numero = 0;
        do {
            pagina = transacaoService.listar(usuarioId, doDia, numero++, TAMANHO_DA_PAGINA);
            pagina.itens().forEach(transacao -> despesas.add(
                new DespesaLancada(transacao.getId(), transacao.getValor(), transacao.getContaId())
            ));
        } while (numero < pagina.totalPaginas());
        return despesas;
    }
}
