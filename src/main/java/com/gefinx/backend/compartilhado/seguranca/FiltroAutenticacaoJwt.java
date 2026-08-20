package com.gefinx.backend.compartilhado.seguranca;

import com.gefinx.backend.usuarios.dominio.ControleDeSessoes;
import com.gefinx.backend.usuarios.infraestrutura.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final ControleDeSessoes controleDeSessoes;

    public FiltroAutenticacaoJwt(JwtService jwtService, ControleDeSessoes controleDeSessoes) {
        this.jwtService = jwtService;
        this.controleDeSessoes = controleDeSessoes;
    }

    /**
     * Assinatura e prazo válidos não bastam: a sessão precisa ainda valer.
     *
     * <p>Sem a segunda conferência, um token entregue não pode mais ser recolhido — o "Sair"
     * da tela apaga só a cópia local, e quem tivesse o token entraria até ele expirar.
     *
     * <p>Sessão recusada não vira erro aqui: o contexto fica sem autenticação e o
     * {@code authenticationEntryPoint} responde `401`, que o frontend já trata como sessão
     * expirada.
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String cabecalhoAutorizacao = request.getHeader("Authorization");

        if (cabecalhoAutorizacao != null && cabecalhoAutorizacao.startsWith(PREFIXO_BEARER)) {
            String token = cabecalhoAutorizacao.substring(PREFIXO_BEARER.length());

            if (jwtService.tokenValido(token)) {
                Long usuarioId = jwtService.extrairIdUsuario(token);
                Instant emitidoEm = jwtService.extrairEmissao(token);

                if (controleDeSessoes.sessaoValida(usuarioId, emitidoEm)) {
                    var autenticacao = new UsernamePasswordAuthenticationToken(usuarioId, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(autenticacao);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
