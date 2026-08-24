package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CalcularSaldoService {

    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioConta repositorioConta;

    public CalcularSaldoService(RepositorioTransacao repositorioTransacao, RepositorioConta repositorioConta) {
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioConta = repositorioConta;
    }

    /** Consolidado: todas as contas do usuário somadas. */
    public ResultadoSaldo calcular(Long usuarioId) {
        return montar(
            repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.RECEITA),
            repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.DESPESA)
        );
    }

    /**
     * De uma conta só. A conta é buscada pela dupla (id, usuário) antes de somar: sem
     * isso, um id na URL leria o saldo da conta de outra pessoa.
     */
    public ResultadoSaldo calcularPorConta(Long usuarioId, Long contaId) {
        repositorioConta.buscarPorIdEUsuario(contaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));

        return montar(
            repositorioTransacao.somarValorPorContaETipo(contaId, TipoTransacao.RECEITA),
            repositorioTransacao.somarValorPorContaETipo(contaId, TipoTransacao.DESPESA)
        );
    }

    private ResultadoSaldo montar(BigDecimal totalReceitas, BigDecimal totalDespesas) {
        return new ResultadoSaldo(totalReceitas, totalDespesas, totalReceitas.subtract(totalDespesas));
    }
}
