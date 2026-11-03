package com.gefinx.backend.planejamento.interfaces.web.dto;

import com.gefinx.backend.planejamento.dominio.CorDaEtiqueta;
import com.gefinx.backend.planejamento.dominio.Etiqueta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * O nome é aparado no serviço, mas o limite daqui conta os espaços das pontas: a tela já os
 * tira antes de enviar. Uma cor fora da lista não chega a ser lida: o corpo inteiro volta
 * {@code 400}.
 */
public record RequisicaoEtiqueta(
    @NotBlank(message = "O nome é obrigatório")
    @Size(max = Etiqueta.NOME_NO_MAXIMO, message = "O nome deve ter no máximo " + Etiqueta.NOME_NO_MAXIMO + " caracteres")
    String nome,

    @NotNull(message = "A cor é obrigatória")
    CorDaEtiqueta cor
) {
}
