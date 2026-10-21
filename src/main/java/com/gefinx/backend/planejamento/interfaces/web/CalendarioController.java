package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.planejamento.aplicacao.CalendarioService;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoPeriodoSemTrabalho;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaCalendario;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaPeriodoSemTrabalho;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Os dias que contam para a meta. Ignorar um feriado é um {@code PUT} na data dele, e voltar a
 * considerá-lo é o {@code DELETE} da mesma rota: os dois podem se repetir sem efeito novo.
 */
@RestController
@RequestMapping("/api/planejamento/calendario")
public class CalendarioController {

    private final CalendarioService calendarioService;

    public CalendarioController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    /** O ano vem sempre da tela, que já sabe qual está mostrando. */
    @GetMapping
    public RespostaCalendario doAno(
        @RequestParam
        @Min(value = 2000, message = "O ano deve ser no mínimo 2000")
        @Max(value = 2100, message = "O ano deve ser no máximo 2100") int ano
    ) {
        return RespostaCalendario.apartirDoDominio(calendarioService.doAno(UsuarioAutenticado.obterId(), ano));
    }

    @PutMapping("/feriados-ignorados/{data}")
    public ResponseEntity<Void> ignorarFeriado(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        calendarioService.ignorarFeriado(UsuarioAutenticado.obterId(), data);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/feriados-ignorados/{data}")
    public ResponseEntity<Void> considerarFeriado(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        calendarioService.considerarFeriado(UsuarioAutenticado.obterId(), data);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/periodos")
    public ResponseEntity<RespostaPeriodoSemTrabalho> adicionarPeriodo(
        @Valid @RequestBody RequisicaoPeriodoSemTrabalho requisicao
    ) {
        RespostaPeriodoSemTrabalho periodo = RespostaPeriodoSemTrabalho.apartirDoDominio(calendarioService.adicionarPeriodo(
            UsuarioAutenticado.obterId(), requisicao.inicio(), requisicao.fim(), requisicao.descricao()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(periodo);
    }

    @DeleteMapping("/periodos/{id}")
    public ResponseEntity<Void> excluirPeriodo(@PathVariable Long id) {
        calendarioService.excluirPeriodo(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
