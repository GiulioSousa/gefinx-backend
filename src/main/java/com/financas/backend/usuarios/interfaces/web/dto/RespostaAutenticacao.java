package com.financas.backend.usuarios.interfaces.web.dto;

public record RespostaAutenticacao(String token, String nome, String email) {
}
