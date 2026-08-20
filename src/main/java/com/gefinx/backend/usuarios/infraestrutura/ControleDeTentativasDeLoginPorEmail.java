package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.NormalizadorDeEmail;
import org.springframework.stereotype.Component;

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
     * Delega a normalização ao {@link NormalizadorDeEmail}, o mesmo ponto que decide qual
     * conta o cadastro e o login enxergam. Manter aqui uma cópia da regra a faria divergir
     * com o tempo, e bastaria alternar a caixa das letras para ganhar um balde novo a cada
     * tentativa — a mesma armadilha da chave derivada do caminho cru que abria o desvio
     * corrigido na Etapa 5: entrada controlada por quem ataca não pode escolher o balde.
     */
    private String chavePara(String email) {
        String normalizado = NormalizadorDeEmail.normalizar(email);
        return PREFIXO_DA_CHAVE + (normalizado == null ? "" : normalizado);
    }
}
