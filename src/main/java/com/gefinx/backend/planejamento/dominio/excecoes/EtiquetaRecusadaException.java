package com.gefinx.backend.planejamento.dominio.excecoes;

/**
 * Uma etiqueta recusada por causa de um dos campos: o nome que já existe, ou, na despesa, uma
 * etiqueta que não é de quem a usa. Carrega o campo para a resposta apontá-lo, como as falhas
 * de validação.
 */
public class EtiquetaRecusadaException extends RuntimeException {

    private final String campo;

    public EtiquetaRecusadaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
