package com.financas.backend.compartilhado.seguranca;

import com.financas.backend.compartilhado.excecoes.ErroResposta;
import com.financas.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Protege as rotas de autenticação contra força bruta, limitando a quantidade de
 * tentativas por origem.
 *
 * <p>A origem vem de {@code getRemoteAddr()}, e não de cabeçalhos como
 * {@code X-Forwarded-For} — que o cliente poderia forjar para escapar do limite.
 * Ao publicar atrás de um proxy reverso, configure
 * {@code server.forward-headers-strategy}, para que o próprio contêiner resolva o
 * endereço real antes de chegar aqui.
 */
@Component
public class FiltroLimiteDeRequisicoes extends OncePerRequestFilter {

    private static final String ROTA_LOGIN = "/api/auth/login";
    private static final String ROTA_REGISTRO = "/api/auth/registrar";

    private final LimitadorDeRequisicoes limitador;
    private final PropriedadesLimiteDeRequisicoes propriedades;
    private final ObjectMapper objectMapper;

    public FiltroLimiteDeRequisicoes(
        LimitadorDeRequisicoes limitador,
        PropriedadesLimiteDeRequisicoes propriedades,
        ObjectMapper objectMapper
    ) {
        this.limitador = limitador;
        this.propriedades = propriedades;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return politicaPara(request) == null;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Politica politica = politicaPara(request);
        String chave = request.getRequestURI() + ":" + request.getRemoteAddr();

        var resultado = limitador.verificar(chave, politica);
        if (!resultado.permitido()) {
            responderLimiteExcedido(response, resultado.segundosParaLiberar());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Politica politicaPara(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case ROTA_LOGIN -> propriedades.login();
            case ROTA_REGISTRO -> propriedades.registro();
            default -> null;
        };
    }

    private void responderLimiteExcedido(HttpServletResponse response, long segundosParaLiberar) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundosParaLiberar));

        ErroResposta corpo = new ErroResposta(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Muitas tentativas. Tente novamente em " + segundosParaLiberar + " segundos."
        );
        objectMapper.writeValue(response.getWriter(), corpo);
    }
}
