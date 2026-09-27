package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CalcularSaldoService;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaSaldo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/saldo")
public class SaldoController {

    private final CalcularSaldoService calcularSaldoService;

    public SaldoController(CalcularSaldoService calcularSaldoService) {
        this.calcularSaldoService = calcularSaldoService;
    }

    /**
     * Sem {@code contaId}, o consolidado — mesma resposta e mesmo formato de antes da
     * Etapa 19, para que quem já consumia a rota não precise mudar. Com {@code contaId},
     * só aquela conta.
     *
     * <p>{@code dataInicio} e {@code dataFim} são o recorte da listagem de transações,
     * com o mesmo contrato: inclusivos, opcionais e independentes entre si. Sem nenhum dos
     * dois a resposta é a de sempre; com eles, os totais e o {@code saldo} descrevem só o
     * período. Combinam com {@code contaId}, como na listagem.
     */
    @GetMapping
    public RespostaSaldo saldo(
        @RequestParam(required = false) Long contaId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        Long usuarioId = UsuarioAutenticado.obterId();
        Periodo periodo = new Periodo(dataInicio, dataFim);
        return RespostaSaldo.apartirDoResultado(
            contaId == null
                ? calcularSaldoService.calcular(usuarioId, periodo)
                : calcularSaldoService.calcularPorConta(usuarioId, contaId, periodo)
        );
    }
}
