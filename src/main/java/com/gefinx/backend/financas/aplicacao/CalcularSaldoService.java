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

    /**
     * Consolidado: todas as contas do usuário somadas.
     *
     * <p>Transferência não entra, e por isso o total continua exatamente o mesmo de antes
     * da Etapa 20. Dinheiro que troca de conta não vira receita nem despesa, e como as
     * duas pontas pertencem ao mesmo usuário, o líquido aqui é zero — sem precisar de
     * consulta para provar.
     */
    public ResultadoSaldo calcular(Long usuarioId) {
        return montar(
            repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.RECEITA),
            repositorioTransacao.somarValorPorUsuarioETipo(usuarioId, TipoTransacao.DESPESA),
            BigDecimal.ZERO
        );
    }

    /**
     * De uma conta só. A conta é buscada pela dupla (id, usuário) antes de somar: sem
     * isso, um id na URL leria o saldo da conta de outra pessoa.
     *
     * <p>Aqui a transferência importa, e vem num campo próprio em vez de misturada nos
     * totais: o extrato de uma conta que recebeu R$ 200 de outra conta do mesmo dono não
     * pode chamar isso de receita — não houve renda nenhuma.
     */
    public ResultadoSaldo calcularPorConta(Long usuarioId, Long contaId) {
        repositorioConta.buscarPorIdEUsuario(contaId, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));

        return montar(
            repositorioTransacao.somarValorPorContaETipo(contaId, TipoTransacao.RECEITA),
            repositorioTransacao.somarValorPorContaETipo(contaId, TipoTransacao.DESPESA),
            repositorioTransacao.somarTransferenciasLiquidasDaConta(contaId)
        );
    }

    private ResultadoSaldo montar(
        BigDecimal totalReceitas,
        BigDecimal totalDespesas,
        BigDecimal totalTransferencias
    ) {
        BigDecimal saldo = totalReceitas.subtract(totalDespesas).add(totalTransferencias);
        return new ResultadoSaldo(totalReceitas, totalDespesas, totalTransferencias, saldo);
    }
}
