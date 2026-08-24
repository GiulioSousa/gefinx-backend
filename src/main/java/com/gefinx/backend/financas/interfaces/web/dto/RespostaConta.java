package com.gefinx.backend.financas.interfaces.web.dto;

import com.gefinx.backend.financas.dominio.Conta;

import java.math.BigDecimal;

public record RespostaConta(Long id, String nome, BigDecimal saldo) {

    public static RespostaConta apartirDoDominio(Conta conta, BigDecimal saldo) {
        return new RespostaConta(conta.getId(), conta.getNome(), saldo);
    }
}
