package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.financas.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.financas.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Implementa o controle por conta sobre o mesmo token bucket já usado pelo limite
 * por endereço, com uma família de chaves própria.
 */
@Component
public class ControleDeTentativasDeLoginPorEmail implements ControleDeTentativasDeLogin {

    private static final String PREFIXO_DA_CHAVE = "login-por-conta:";

    private final LimitadorDeRequisicoes limitador;
    private final PropriedadesLimiteDeRequisicoes.Politica politica;

    public ControleDeTentativasDeLoginPorEmail(
        LimitadorDeRequisicoes limitador,
        PropriedadesLimiteDeRequisicoes propriedades
    ) {
        this.limitador = limitador;
        this.politica = propriedades.loginPorConta();
    }

    @Override
    public ResultadoDaTentativa registrar(String email) {
        var resultado = limitador.verificar(chavePara(email), politica);
        return new ResultadoDaTentativa(!resultado.permitido(), resultado.segundosParaLiberar());
    }

    @Override
    public void liberar(String email) {
        limitador.reiniciar(chavePara(email));
    }

    /**
     * A normalização não é cosmética: sem ela, {@code Alvo@exemplo.com} e
     * {@code alvo@exemplo.com} cairiam em baldes distintos e bastaria alternar a caixa
     * para tentar à vontade. É a mesma armadilha da chave derivada do caminho cru que
     * abria o desvio corrigido na Etapa 5 — entrada controlada por quem ataca não pode
     * escolher o balde.
     */
    private String chavePara(String email) {
        String normalizado = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return PREFIXO_DA_CHAVE + normalizado;
    }
}
