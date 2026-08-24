package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CalcularSaldoService;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaSaldo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     */
    @GetMapping
    public RespostaSaldo saldo(@RequestParam(required = false) Long contaId) {
        Long usuarioId = UsuarioAutenticado.obterId();
        return RespostaSaldo.apartirDoResultado(
            contaId == null
                ? calcularSaldoService.calcular(usuarioId)
                : calcularSaldoService.calcularPorConta(usuarioId, contaId)
        );
    }
}
