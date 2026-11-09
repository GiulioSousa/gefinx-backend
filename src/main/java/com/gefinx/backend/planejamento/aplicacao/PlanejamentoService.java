package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
import com.gefinx.backend.planejamento.dominio.CategoriaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.ContaDoPlanejamento;
import com.gefinx.backend.planejamento.dominio.CustoDiario;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.Lancamento;
import com.gefinx.backend.planejamento.dominio.PlanoDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioCategoriasDoCustoDiario;
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
    private final DespesaPlanejadaService despesaPlanejadaService;
    private final RepositorioContasDeFora repositorioContasDeFora;
    private final RepositorioCategoriasDoCustoDiario repositorioCategoriasDoCustoDiario;
    private final FonteDeSaldo fonteDeSaldo;
    private final CalendarioService calendarioService;
    private final Clock relogio;

    public PlanejamentoService(
        RepositorioDespesaPlanejada repositorioDespesa,
        DespesaPlanejadaService despesaPlanejadaService,
        RepositorioContasDeFora repositorioContasDeFora,
        RepositorioCategoriasDoCustoDiario repositorioCategoriasDoCustoDiario,
        FonteDeSaldo fonteDeSaldo,
        CalendarioService calendarioService,
        Clock relogio
    ) {
        this.repositorioDespesa = repositorioDespesa;
        this.despesaPlanejadaService = despesaPlanejadaService;
        this.repositorioContasDeFora = repositorioContasDeFora;
        this.repositorioCategoriasDoCustoDiario = repositorioCategoriasDoCustoDiario;
        this.fonteDeSaldo = fonteDeSaldo;
        this.calendarioService = calendarioService;
        this.relogio = relogio;
    }

    /**
     * "Hoje" vem do relógio configurado no fuso de quem usa, e não do fuso do servidor: num
     * servidor em UTC, das 21h à meia-noite o plano já estaria no dia seguinte — o mesmo
     * defeito que a data padrão do formulário de transação teve no frontend.
     *
     * <p><b>Pagar uma despesa planejada não mexe na meta.</b> O pagamento tira o valor do saldo
     * de agora, e a despesa sai da lista: as duas coisas se anulam. Até a Etapa 42, com a meta
     * presa ao saldo de ontem, era preciso descontar os pagamentos de hoje também dele.
     *
     * <p><b>O custo diário</b> soma as despesas das categorias escolhidas nos 30 dias até ontem,
     * e divide cada categoria pelos dias em que houve gasto nela, com três exclusões: as das contas de fora, que nunca passaram pelo saldo do planejamento;
     * os pagamentos de despesa planejada, que já estavam no plano — um "Mercado do mês" pago em
     * Alimentação seria cobrado duas vezes —; e as de hoje, para a média não mudar a cada gasto.
     * O gasto de hoje nessas categorias é devolvido ao saldo da meta (ver {@link PlanoDePagamento}).
     *
     * <p><b>As receitas de hoje</b> também deixam de fora as contas de fora, pelo mesmo motivo.
     *
     * <p>Antes de montar, as séries recorrentes são postas em dia: a ocorrência atrasada não
     * segura a seguinte fora do plano.
     */
    public PlanoDePagamento montar(Long usuarioId) {
        despesaPlanejadaService.colocarSeriesEmDia(usuarioId);
        LocalDate hoje = LocalDate.now(relogio);
        Set<Long> contasDeFora = repositorioContasDeFora.listarPorUsuario(usuarioId);
        Set<Long> categoriasDoCusto = repositorioCategoriasDoCustoDiario.listarPorUsuario(usuarioId);
        CalendarioDeTrabalho calendario = calendarioService.doUsuario(usuarioId);

        BigDecimal saldoAtual = fonteDeSaldo.saldoAte(usuarioId, hoje, contasDeFora);

        CustoDiario custoDiario = null;
        BigDecimal gastoDeHojeNoCusto = BigDecimal.ZERO;
        if (!categoriasDoCusto.isEmpty()) {
            List<Lancamento> gastosDoCusto = gastosDoCustoDiario(usuarioId, hoje, contasDeFora, categoriasDoCusto);
            custoDiario = CustoDiario.calcular(hoje, categoriasDoCusto, gastosDoCusto.stream()
                .filter(gasto -> gasto.data().isBefore(hoje))
                .toList());
            gastoDeHojeNoCusto = somar(gastosDoCusto.stream()
                .filter(gasto -> gasto.data().isEqual(hoje))
                .toList());
        }

        BigDecimal receitasDeHoje = somar(fonteDeSaldo.receitasLancadas(usuarioId, hoje, hoje).stream()
            .filter(receita -> !contasDeFora.contains(receita.contaId()))
            .toList());

        return PlanoDePagamento.calcular(
            hoje, saldoAtual, saldoAtual.add(gastoDeHojeNoCusto), receitasDeHoje, custoDiario,
            repositorioDespesa.listarPendentesPorUsuario(usuarioId), calendario
        );
    }

    /** As despesas das categorias escolhidas, da janela até hoje, sem as de fora e sem pagamento de planejada. */
    private List<Lancamento> gastosDoCustoDiario(
        Long usuarioId,
        LocalDate hoje,
        Set<Long> contasDeFora,
        Set<Long> categorias
    ) {
        List<Lancamento> candidatos = fonteDeSaldo.despesasLancadas(usuarioId, CustoDiario.inicioDaJanela(hoje), hoje)
            .stream()
            .filter(despesa -> categorias.contains(despesa.categoriaId()))
            .filter(despesa -> !contasDeFora.contains(despesa.contaId()))
            .toList();
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        Set<Long> pagamentos = repositorioDespesa.filtrarPagamentos(
            usuarioId, candidatos.stream().map(Lancamento::transacaoId).toList()
        );
        return candidatos.stream()
            .filter(despesa -> !pagamentos.contains(despesa.transacaoId()))
            .toList();
    }

    private static BigDecimal somar(List<Lancamento> lancamentos) {
        return lancamentos.stream().map(Lancamento::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public List<CategoriaDoPlanejamento> listarCategoriasDeDespesa(Long usuarioId) {
        return fonteDeSaldo.listarCategoriasDeDespesa(usuarioId);
    }

    public Set<Long> listarCategoriasDoCustoDiario(Long usuarioId) {
        return repositorioCategoriasDoCustoDiario.listarPorUsuario(usuarioId);
    }

    /**
     * Só categoria de despesa do usuário. A de receita é recusada como a alheia, com `404`:
     * ela não está na lista que a tela oferece, e o banco, que só amarra o dono, a aceitaria.
     */
    @Transactional
    public void definirCategoriasDoCustoDiario(Long usuarioId, Set<Long> categoriaIds) {
        Set<Long> categoriasDeDespesa = fonteDeSaldo.listarCategoriasDeDespesa(usuarioId).stream()
            .map(CategoriaDoPlanejamento::id)
            .collect(Collectors.toSet());

        if (!categoriasDeDespesa.containsAll(categoriaIds)) {
            throw new RecursoNaoEncontradoNoPlanejamentoException("Categoria não encontrada");
        }
        repositorioCategoriasDoCustoDiario.substituir(usuarioId, categoriaIds);
    }
}
