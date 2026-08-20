package com.gefinx.backend.usuarios.dominio.excecoes;

public class TentativasExcedidasException extends RuntimeException {

    private final long segundosParaLiberar;

    public TentativasExcedidasException(long segundosParaLiberar) {
        super("Muitas tentativas. Tente novamente em " + segundosParaLiberar + " segundos.");
        this.segundosParaLiberar = segundosParaLiberar;
    }

    public long getSegundosParaLiberar() {
        return segundosParaLiberar;
    }
}
