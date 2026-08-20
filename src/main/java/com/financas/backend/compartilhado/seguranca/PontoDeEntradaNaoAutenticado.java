package com.financas.backend.compartilhado.seguranca;

import com.financas.backend.compartilhado.excecoes.ErroResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Responde `401` no mesmo formato dos demais erros da API.
 *
 * <p>Antes, a recusa saía por {@code response.sendError}, que devolve
 * {@code Content-Length: 0}. Quem chamava recebia um `401` mudo e caía na mensagem
 * genérica do cliente — a única resposta da API fora do formato uniforme da seção 6,
 * justamente na que o frontend mais lê, já que é ela que decide encerrar a sessão.
 *
 * <p>O corpo é escrito com {@code setStatus} em vez de {@code sendError} de propósito:
 * {@code sendError} entrega a resposta à página de erro do contêiner, que descartaria
 * o que fosse escrito aqui.
 *
 * <p>A mensagem não distingue token ausente de token expirado ou revogado. A diferença
 * não muda o que o cliente faz — reautenticar —, e enunciá-la contaria a quem sonda em
 * que estado está o token que ele tem em mãos.
 */
@Component
public class PontoDeEntradaNaoAutenticado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public PontoDeEntradaNaoAutenticado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException excecaoDeAutenticacao
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErroResposta corpo = new ErroResposta(HttpStatus.UNAUTHORIZED.value(), "Não autenticado");
        objectMapper.writeValue(response.getWriter(), corpo);
    }
}
