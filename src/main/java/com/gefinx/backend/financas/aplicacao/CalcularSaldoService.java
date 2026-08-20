package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CalcularSaldoService {

    private final RepositorioTransacao repositorioTransacao;

    public CalcularSaldoService(RepositorioTransacao repositorioTransacao) {
        this.repositorioTransacao = repositorioTransacao;
    }

    public ResultadoSaldo calcular(Long usuarioId) {
        BigDecimal totalReceitas = repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.RECEITA);
        BigDecimal totalDespesas = repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.DESPESA);
        BigDecimal saldo = totalReceitas.subtract(totalDespesas);
        return new ResultadoSaldo(totalReceitas, totalDespesas, saldo);
    }
}
