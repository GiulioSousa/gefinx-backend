package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioContasDeFora;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlanejamentoService {

    private final RepositorioDespesaPlanejada repositorioDespesa;
    private final RepositorioContasDeFora repositorioContasDeFora;
    private final FonteDeSaldo fonteDeSaldo;
    private final Clock relogio;

    public PlanejamentoService(
        RepositorioDespesaPlanejada repositorioDespesa,
        RepositorioContasDeFora repositorioContasDeFora,
        FonteDeSaldo fonteDeSaldo,
        Clock relogio
    ) {
        this.repositorioDespesa = repositorioDespesa;
        this.repositorioContasDeFora = repositorioContasDeFora;
        this.fonteDeSaldo = fonteDeSaldo;
        this.relogio = relogio;
    }

    /**
     * "Hoje" vem do relógio configurado no fuso de quem usa, e não do fuso do servidor: num
     * servidor em UTC, das 21h à meia-noite o plano já estaria no dia seguinte — o mesmo
     * defeito que a data padrão do formulário de transação teve no frontend.
     */
    public PlanoDePagamento montar(Long usuarioId) {
        LocalDate hoje = LocalDate.now(relogio);
        Set<Long> contasDeFora = repositorioContasDeFora.listarPorUsuario(usuarioId);

        BigDecimal saldoDeOntem = fonteDeSaldo.saldoAte(usuarioId, hoje.minusDays(1), contasDeFora);
        BigDecimal saldoAtual = fonteDeSaldo.saldoAte(usuarioId, hoje, contasDeFora);

        return PlanoDePagamento.calcular(
            hoje, saldoDeOntem, saldoAtual, repositorioDespesa.listarPorUsuario(usuarioId),
            CalendarioDeTrabalho.SEGUNDA_A_SABADO
        );
    }

    public List<ContaDoPlanejamento> listarContas(Long usuarioId) {
        return fonteDeSaldo.listarContas(usuarioId);
    }

    public Set<Long> listarContasDeFora(Long usuarioId) {
        return repositorioContasDeFora.listarPorUsuario(usuarioId);
    }

    /**
     * Recusa a lista inteira se um dos ids não for conta do usuário. O banco também
     * recusaria, pela chave composta — mas como violação de integridade, que viraria `500`
     * em vez do `404` que o resto da API dá a um id alheio.
     */
    @Transactional
    public void definirContasDeFora(Long usuarioId, Set<Long> contaIds) {
        Set<Long> contasDoUsuario = fonteDeSaldo.listarContas(usuarioId).stream()
            .map(ContaDoPlanejamento::id)
            .collect(Collectors.toSet());

        if (!contasDoUsuario.containsAll(contaIds)) {
            throw new RecursoNaoEncontradoNoPlanejamentoException("Conta não encontrada");
        }
        repositorioContasDeFora.substituir(usuarioId, contaIds);
    }
}
