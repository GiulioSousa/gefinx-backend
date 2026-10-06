package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.RelatorioService;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaMesDoRelatorio;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaTotalDaCategoria;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

/**
 * Os meses chegam e saem como {@code 2026-10}: o relatório pensa em meses, e uma data com dia
 * pediria ao cliente escolher um dia que não significa nada aqui.
 */
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    /** Até 24 meses, inclusive as duas pontas. */
    @GetMapping("/mensal")
    public List<RespostaMesDoRelatorio> mensal(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth inicio,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth fim
    ) {
        return relatorioService.mensal(UsuarioAutenticado.obterId(), inicio, fim).stream()
            .map(RespostaMesDoRelatorio::apartirDoResumo)
            .toList();
    }

    @GetMapping("/categorias")
    public List<RespostaTotalDaCategoria> porCategoria(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes,
        @RequestParam(defaultValue = "DESPESA") TipoTransacao tipo
    ) {
        return relatorioService.porCategoria(UsuarioAutenticado.obterId(), mes, tipo).stream()
            .map(RespostaTotalDaCategoria::apartirDoTotal)
            .toList();
    }
}
