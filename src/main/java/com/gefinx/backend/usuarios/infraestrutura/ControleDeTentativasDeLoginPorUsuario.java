package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.compartilhado.seguranca.LimitadorDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import com.gefinx.backend.usuarios.dominio.ControleDeTentativasDeLogin;
import com.gefinx.backend.usuarios.dominio.NormalizadorDeUsuario;
import org.springframework.stereotype.Component;

/**
 * Implementa o controle por conta sobre o mesmo token bucket já usado pelo limite
 * por endereço, com uma família de chaves própria.
 */
@Component
public class ControleDeTentativasDeLoginPorUsuario implements ControleDeTentativasDeLogin {

    private static final String PREFIXO_DA_CHAVE = "login-por-conta:";

    private final LimitadorDeRequisicoes limitador;
    private final PropriedadesLimiteDeRequisicoes.Politica politica;

    public ControleDeTentativasDeLoginPorUsuario(
        LimitadorDeRequisicoes limitador,
        PropriedadesLimiteDeRequisicoes propriedades
    ) {
        this.limitador = limitador;
        this.politica = propriedades.loginPorConta();
    }

    @Override
    public ResultadoDaTentativa registrar(String usuario) {
        var resultado = limitador.verificar(chavePara(usuario), politica);
        return new ResultadoDaTentativa(!resultado.permitido(), resultado.segundosParaLiberar());
    }

    @Override
    public void liberar(String usuario) {
        limitador.reiniciar(chavePara(usuario));
    }

    /**
     * Delega a normalização ao {@link NormalizadorDeUsuario}, o mesmo ponto que decide qual
     * conta o login enxerga. Manter aqui uma cópia da regra a faria divergir com o tempo, e
     * bastaria alternar a caixa das letras para ganhar um balde novo a cada tentativa — a
     * mesma armadilha da chave derivada do caminho cru que abria o desvio corrigido na
     * Etapa 5: entrada controlada por quem ataca não pode escolher o balde.
     */
    private String chavePara(String usuario) {
        String normalizado = NormalizadorDeUsuario.normalizar(usuario);
        return PREFIXO_DA_CHAVE + (normalizado == null ? "" : normalizado);
    }
}
