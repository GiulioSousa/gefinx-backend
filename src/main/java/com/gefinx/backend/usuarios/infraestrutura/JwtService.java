package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.usuarios.dominio.Usuario;
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

    /**
     * Instante de emissão em milissegundos.
     *
     * <p>O {@code iat} padrão guarda apenas segundos, e essa granularidade não serve para
     * decidir revogação: com ela, revogar e reautenticar dentro do mesmo segundo vira um
     * empate impossível de desfazer — ou o token revogado sobrevive, ou o login seguinte é
     * recusado. Um claim próprio, em milissegundos, elimina o empate em vez de escolher qual
     * dos dois lados sacrificar.
     *
     * <p>O {@code iat} continua sendo emitido, por ser o campo que qualquer ferramenta espera.
     */
    private static final String CLAIM_EMISSAO_MS = "emissaoMs";

    private final SecretKey chaveSecreta;
    private final long expiracaoMinutos;

    public JwtService(
        @Value("${gefinx.jwt.secret}") String segredoBase64,
        @Value("${gefinx.jwt.expiracao-minutos}") long expiracaoMinutos
    ) {
        this.chaveSecreta = Keys.hmacShaKeyFor(Decoders.BASE64.decode(segredoBase64));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    public String gerarToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
            .subject(usuario.getId().toString())
            .claim("usuario", usuario.getUsuario())
            .claim(CLAIM_EMISSAO_MS, agora.toEpochMilli())
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

    /**
     * Instante de emissão usado na conferência de revogação.
     *
     * <p>Recai no {@code iat} quando o claim em milissegundos não existe, para que os tokens
     * emitidos antes desta mudança continuem valendo até expirarem — do contrário a mudança
     * deslogaria todo mundo de uma vez.
     */
    public Instant extrairEmissao(String token) {
        Claims claims = extrairClaims(token);
        Long emissaoMs = claims.get(CLAIM_EMISSAO_MS, Long.class);

        return emissaoMs != null ? Instant.ofEpochMilli(emissaoMs) : claims.getIssuedAt().toInstant();
    }

    private Claims extrairClaims(String token) {
        return Jwts.parser()
            .verifyWith(chaveSecreta)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
