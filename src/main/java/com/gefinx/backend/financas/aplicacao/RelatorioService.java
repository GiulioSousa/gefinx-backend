package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.MovimentoDoMes;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.excecoes.RelatorioInvalidoException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * O relatório mensal: o movimento de cada mês e o saldo com que ele terminou, e onde o dinheiro
 * de um mês foi, por categoria. Só leitura — as somas são as mesmas do saldo, agrupadas.
 */
@Service
public class RelatorioService {

    /** Dois anos: o bastante para comparar o mesmo mês de um ano com o do anterior. */
    static final int MAXIMO_DE_MESES = 24;

    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioCategoria repositorioCategoria;
    private final CalcularSaldoService calcularSaldoService;

    public RelatorioService(
        RepositorioTransacao repositorioTransacao,
        RepositorioCategoria repositorioCategoria,
        CalcularSaldoService calcularSaldoService
    ) {
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioCategoria = repositorioCategoria;
        this.calcularSaldoService = calcularSaldoService;
    }

    /**
     * Um item por mês de {@code inicio} a {@code fim}, inclusive — também os meses sem lançamento,
     * com zero, para a série não ter buracos que um gráfico desenharia como meses vizinhos.
     *
     * <p>{@code saldoAoFim} parte do saldo consolidado até o fim do mês anterior ao início e soma
     * o resultado de cada mês. É o mesmo número que o Painel mostraria com o período "até o fim
     * daquele mês", sem uma consulta por mês.
     */
    public List<ResumoDoMes> mensal(Long usuarioId, YearMonth inicio, YearMonth fim) {
        if (fim.isBefore(inicio)) {
            throw new RelatorioInvalidoException("O mês final não pode ser anterior ao inicial");
        }
        if (ChronoUnit.MONTHS.between(inicio, fim) + 1 > MAXIMO_DE_MESES) {
            throw new RelatorioInvalidoException("O relatório cobre no máximo " + MAXIMO_DE_MESES + " meses");
        }

        BigDecimal saldo = calcularSaldoService
            .calcular(usuarioId, new Periodo(null, inicio.atDay(1).minusDays(1)))
            .saldo();
        Map<YearMonth, MovimentoDoMes> porMes = repositorioTransacao
            .somarPorMes(usuarioId, new Periodo(inicio.atDay(1), fim.atEndOfMonth())).stream()
            .collect(Collectors.toMap(MovimentoDoMes::mes, Function.identity()));

        List<ResumoDoMes> meses = new ArrayList<>();
        for (YearMonth mes = inicio; !mes.isAfter(fim); mes = mes.plusMonths(1)) {
            MovimentoDoMes movimento = porMes.getOrDefault(mes, MovimentoDoMes.vazio(mes));
            BigDecimal resultado = movimento.receitas().subtract(movimento.despesas());
            saldo = saldo.add(resultado);
            meses.add(new ResumoDoMes(mes, movimento.receitas(), movimento.despesas(), resultado, saldo));
        }
        return meses;
    }

    /** Do maior total para o menor: a pergunta é para onde foi a maior parte. */
    public List<TotalDaCategoria> porCategoria(Long usuarioId, YearMonth mes, TipoTransacao tipo) {
        if (tipo == TipoTransacao.TRANSFERENCIA) {
            throw new RelatorioInvalidoException("Transferência não tem categoria");
        }
        Map<Long, String> nomes = repositorioCategoria.listarPorUsuario(usuarioId).stream()
            .collect(Collectors.toMap(Categoria::getId, Categoria::getNome));
        return repositorioTransacao
            .somarPorCategoria(usuarioId, tipo, new Periodo(mes.atDay(1), mes.atEndOfMonth())).stream()
            .map(total -> new TotalDaCategoria(total.categoriaId(), nomes.get(total.categoriaId()), total.total()))
            .sorted(Comparator.comparing(TotalDaCategoria::total).reversed())
            .toList();
    }

    public record ResumoDoMes(
        YearMonth mes,
        BigDecimal receitas,
        BigDecimal despesas,
        BigDecimal resultado,
        BigDecimal saldoAoFim
    ) {
    }

    public record TotalDaCategoria(Long categoriaId, String nome, BigDecimal total) {
    }
}
