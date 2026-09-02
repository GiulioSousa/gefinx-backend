package com.gefinx.backend.apoio;

import com.gefinx.backend.usuarios.dominio.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * Cria contas de acesso para os testes que precisam de um usuário de verdade no banco.
 *
 * <p>Existe porque não há mais {@code POST /api/auth/registrar}: a conta nasce por INSERT,
 * do mesmo jeito que nasce em produção pelo {@code db/criar-usuario.sql}. Testar contra o
 * caminho real vale mais do que a conveniência de uma rota que o sistema não tem.
 *
 * <p>Grava só o usuário — sem categorias nem conta padrão. Quem precisa delas as cria no
 * próprio cenário; a semente do script de produção é conveniência de quem opera, e um
 * teste que dependesse dela estaria testando a semente, não o que se propõe a testar.
 */
public final class ContasDeTeste {

    public static final String SENHA = "uma frase de senha";

    private static final PasswordEncoder CODIFICADOR = new BCryptPasswordEncoder();

    private ContasDeTeste() {
    }

    /**
     * A marca de revogação nasce um minuto atrás, e não em {@code now()}, para que o token
     * emitido logo em seguida seja sempre posterior a ela. Com os dois instantes colados,
     * qualquer diferença de relógio entre o banco e a JVM derrubaria o token recém-emitido
     * como revogado — uma falha intermitente e sem relação com o que se está testando.
     */
    public static Usuario criar(JdbcTemplate jdbcTemplate, String nomeDeUsuario) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime sessoesValidasApos = agora.minusMinutes(1);
        String senhaHash = CODIFICADOR.encode(SENHA);

        Long id = jdbcTemplate.queryForObject(
            """
            INSERT INTO usuarios (usuario, senha_hash, criado_em, sessoes_validas_apos)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """,
            Long.class, nomeDeUsuario, senhaHash, agora, sessoesValidasApos
        );

        return new Usuario(id, nomeDeUsuario, senhaHash, agora, sessoesValidasApos);
    }
}
