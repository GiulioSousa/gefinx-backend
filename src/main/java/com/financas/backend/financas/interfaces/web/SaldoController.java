package com.financas.backend.financas.interfaces.web;

import com.financas.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.financas.backend.financas.aplicacao.CalcularSaldoService;
import com.financas.backend.financas.interfaces.web.dto.RespostaSaldo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/saldo")
public class SaldoController {

    private final CalcularSaldoService calcularSaldoService;

    public SaldoController(CalcularSaldoService calcularSaldoService) {
        this.calcularSaldoService = calcularSaldoService;
    }

    @GetMapping
    public RespostaSaldo saldo() {
        return RespostaSaldo.apartirDoResultado(calcularSaldoService.calcular(UsuarioAutenticado.obterId()));
    }
}
