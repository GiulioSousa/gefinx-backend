package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.CalendarioDeTrabalho;
import com.gefinx.backend.planejamento.dominio.Feriado;
import com.gefinx.backend.planejamento.dominio.FeriadosNacionais;
import com.gefinx.backend.planejamento.dominio.PeriodoSemTrabalho;
import com.gefinx.backend.planejamento.dominio.RepositorioFeriadosIgnorados;
import com.gefinx.backend.planejamento.dominio.RepositorioPeriodosSemTrabalho;
import com.gefinx.backend.planejamento.dominio.excecoes.PeriodoSemTrabalhoRecusadoException;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Os dias que contam para a meta: os feriados nacionais, com os que se decidiu ignorar, e os
 * períodos sem trabalho de cada um.
 */
@Service
public class CalendarioService {

    private final RepositorioFeriadosIgnorados repositorioFeriadosIgnorados;
    private final RepositorioPeriodosSemTrabalho repositorioPeriodos;

    public CalendarioService(
        RepositorioFeriadosIgnorados repositorioFeriadosIgnorados,
        RepositorioPeriodosSemTrabalho repositorioPeriodos
    ) {
        this.repositorioFeriadosIgnorados = repositorioFeriadosIgnorados;
        this.repositorioPeriodos = repositorioPeriodos;
    }

    public record FeriadoDoAno(Feriado feriado, boolean ignorado) {
    }

    /** Os períodos são os que tocam o ano, inclusive os que começam no anterior ou terminam no seguinte. */
    public record CalendarioDoAno(int ano, List<FeriadoDoAno> feriados, List<PeriodoSemTrabalho> periodos) {
    }

    public CalendarioDeTrabalho doUsuario(Long usuarioId) {
        return CalendarioDeTrabalho.comFeriadosNacionais(
            repositorioFeriadosIgnorados.listarPorUsuario(usuarioId),
            repositorioPeriodos.listarPorUsuario(usuarioId)
        );
    }

    public CalendarioDoAno doAno(Long usuarioId, int ano) {
        Set<LocalDate> ignorados = repositorioFeriadosIgnorados.listarPorUsuario(usuarioId);
        PeriodoSemTrabalho anoInteiro = new PeriodoSemTrabalho(null, LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31), null);

        return new CalendarioDoAno(
            ano,
            FeriadosNacionais.doAno(ano).stream()
                .map(feriado -> new FeriadoDoAno(feriado, ignorados.contains(feriado.data())))
                .toList(),
            repositorioPeriodos.listarPorUsuario(usuarioId).stream()
                .filter(anoInteiro::sobrepoe)
                .toList()
        );
    }

    public void ignorarFeriado(Long usuarioId, LocalDate data) {
        exigirFeriado(data);
        repositorioFeriadosIgnorados.ignorar(usuarioId, data);
    }

    public void considerarFeriado(Long usuarioId, LocalDate data) {
        exigirFeriado(data);
        repositorioFeriadosIgnorados.considerar(usuarioId, data);
    }

    /**
     * Sem {@code fim}, é um dia só. Recusa o período que não muda nada na meta — só domingo e
     * feriado dentro — para que a lista não guarde o que parece valer e não vale; e o que se
     * sobrepõe a outro, para que cada dia apareça num período só.
     */
    @Transactional
    public PeriodoSemTrabalho adicionarPeriodo(Long usuarioId, LocalDate inicio, LocalDate fim, String descricao) {
        LocalDate ate = fim == null ? inicio : fim;
        if (ate.isBefore(inicio)) {
            throw new PeriodoSemTrabalhoRecusadoException("fim", "O fim não pode vir antes do início");
        }

        PeriodoSemTrabalho novo = new PeriodoSemTrabalho(null, inicio, ate, descricaoLimpa(descricao));
        if (novo.dias() > PeriodoSemTrabalho.DIAS_NO_MAXIMO) {
            throw new PeriodoSemTrabalhoRecusadoException(
                "fim", "O período pode ter no máximo " + PeriodoSemTrabalho.DIAS_NO_MAXIMO + " dias"
            );
        }

        List<PeriodoSemTrabalho> existentes = repositorioPeriodos.listarPorUsuario(usuarioId);
        for (PeriodoSemTrabalho existente : existentes) {
            if (existente.sobrepoe(novo)) {
                throw new PeriodoSemTrabalhoRecusadoException(
                    "inicio", "Estes dias já estão em outro período: " + existente.nome()
                );
            }
        }

        CalendarioDeTrabalho semPeriodos = CalendarioDeTrabalho.comFeriadosNacionais(
            repositorioFeriadosIgnorados.listarPorUsuario(usuarioId), List.of()
        );
        if (semPeriodos.contarDias(inicio, ate.plusDays(1)) == 0) {
            String mensagem = inicio.equals(ate)
                ? "Este dia já não é de trabalho: " + semPeriodos.motivoDaFolga(inicio).orElseThrow()
                : "Não há dia de trabalho neste período";
            throw new PeriodoSemTrabalhoRecusadoException("inicio", mensagem);
        }

        return repositorioPeriodos.salvar(usuarioId, novo);
    }

    public void excluirPeriodo(Long usuarioId, Long id) {
        if (!repositorioPeriodos.excluir(usuarioId, id)) {
            throw new RecursoNaoEncontradoNoPlanejamentoException("Período não encontrado");
        }
    }

    private static void exigirFeriado(LocalDate data) {
        if (FeriadosNacionais.em(data).isEmpty()) {
            throw new RecursoNaoEncontradoNoPlanejamentoException("Feriado não encontrado");
        }
    }

    private static String descricaoLimpa(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.strip();
    }
}
