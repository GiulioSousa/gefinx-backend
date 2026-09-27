package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.financas.aplicacao.CalcularSaldoService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * A ponte com finanças, e o único lugar do planejamento que conhece classes de lá. Chama os
 * serviços de aplicação, e não os repositórios: o saldo tem regras — transferência fora do
 * consolidado, dentro do saldo por conta — que só os serviços sabem aplicar.
 */
@Component
public class FonteDeSaldoViaFinancas implements FonteDeSaldo {

    private final CalcularSaldoService calcularSaldoService;
    private final ContaService contaService;

    public FonteDeSaldoViaFinancas(CalcularSaldoService calcularSaldoService, ContaService contaService) {
        this.calcularSaldoService = calcularSaldoService;
        this.contaService = contaService;
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
}
