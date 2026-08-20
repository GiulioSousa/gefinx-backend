package com.gefinx.backend.usuarios.infraestrutura;

import com.gefinx.backend.usuarios.dominio.ControleDeSessoes;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Guarda a marca de revogação em memória para não consultar o banco a cada requisição.
 *
 * <p>Até aqui a autenticação não tocava o banco em requisição alguma — a assinatura do token
 * bastava. Conferir a marca sem cache trocaria isso por uma consulta por requisição, no
 * caminho mais quente do sistema.
 *
 * <p>O cache é invalidado explicitamente ao encerrar sessões, de modo que a revogação vale de
 * imediato; a expiração por tempo é apenas rede de proteção para alteração feita fora da
 * aplicação. Sem essa invalidação a escolha seria entre revogação lenta e consulta sempre.
 *
 * <p>O teto de tamanho existe pelo mesmo motivo do limitador de requisições: mapa que cresce
 * sem limite é, por si só, um vetor.
 */
@Component
public class ControleDeSessoesComCache implements ControleDeSessoes {

    private static final Duration REDE_DE_PROTECAO = Duration.ofMinutes(5);

    private final UsuarioSpringDataRepository repositorio;

    private final Cache<Long, LocalDateTime> marcaPorUsuario = Caffeine.newBuilder()
        .expireAfterWrite(REDE_DE_PROTECAO.toMinutes(), TimeUnit.MINUTES)
        .maximumSize(50_000)
        .build();

    public ControleDeSessoesComCache(UsuarioSpringDataRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Vale o token emitido depois da marca.
     *
     * <p>A comparação é em milissegundos dos dois lados: o token carrega um claim próprio
     * com essa precisão justamente porque o {@code iat} padrão, em segundos, criava um
     * empate insolúvel entre revogar e reautenticar no mesmo segundo.
     *
     * <p>Usuário inexistente cai aqui como sessão inválida: a consulta não devolve marca, o
     * Caffeine não guarda ausência, e o token deixa de autenticar.
     */
    @Override
    public boolean sessaoValida(Long usuarioId, Instant emitidoEm) {
        LocalDateTime marca = marcaPorUsuario.get(usuarioId,
            id -> repositorio.buscarSessoesValidasApos(id).orElse(null));

        if (marca == null) {
            return false;
        }

        return emitidoEm.isAfter(marca.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Marca o instante exato: com a emissão em milissegundos não há mais empate a desfazer.
     *
     * <p>Antes, com precisão de segundos, era preciso arredondar para cima para não deixar
     * escapar token emitido no mesmo segundo — e isso, por sua vez, recusava o login feito
     * logo em seguida. Um teste intermitente expôs os dois lados: passava com o contexto
     * frio, falhava com o contexto quente.
     */
    @Override
    public void encerrarTodas(Long usuarioId) {
        repositorio.atualizarSessoesValidasApos(usuarioId, LocalDateTime.now());
        marcaPorUsuario.invalidate(usuarioId);
    }
}
