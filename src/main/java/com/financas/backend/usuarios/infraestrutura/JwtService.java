package com.financas.backend.usuarios.infraestrutura;

import com.financas.backend.usuarios.dominio.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey chaveSecreta;
    private final long expiracaoMinutos;

    public JwtService(
        @Value("${financas.jwt.secret}") String segredoBase64,
        @Value("${financas.jwt.expiracao-minutos}") long expiracaoMinutos
    ) {
        this.chaveSecreta = Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredoBase64));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
            .subject(usuario.getId().toString())
            .claim("email", usuario.getEmail())
            .claim("nome", usuario.getNome())
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)))
            .signWith(chaveSecreta)
            .compact();
    }

    public boolean tokenValido(String token) {
        try {
            extrairClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException excecao) {
            return false;
        }
    }

    public Long extrairIdUsuario(String token) {
        return Long.valueOf(extrairClaims(token).getSubject());
    }

    private Claims extrairClaims(String token) {
        return Jwts.parser()
            .verifyWith(chaveSecreta)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
