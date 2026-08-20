package com.financas.backend.compartilhado.seguranca;

import com.financas.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Limitador de taxa por chave, baseado em token bucket.
 *
 * <p>Os buckets vivem em memória, expirando por inatividade — suficiente para uma
 * instância única. Ao dividir a aplicação em serviços, este estado precisa migrar
 * para um armazenamento compartilhado (o Bucket4j oferece adaptadores para Redis
 * e Hazelcast), caso contrário cada instância aplicaria o limite isoladamente.
 */
@Component
public class LimitadorDeRequisicoes {

    private static final Duration EXPIRACAO_POR_INATIVIDADE = Duration.ofHours(1);

    private final Cache<String, Bucket> bucketsPorChave = Caffeine.newBuilder()
        .expireAfterAccess(EXPIRACAO_POR_INATIVIDADE.toMinutes(), TimeUnit.MINUTES)
        .maximumSize(100_000)
        .build();

    public ResultadoLimite verificar(String chave, Politica politica) {
        Bucket bucket = bucketsPorChave.get(chave, ignorada -> construirBucket(politica));
        ConsumptionProbe sonda = bucket.tryConsumeAndReturnRemaining(1);

        if (sonda.isConsumed()) {
            return ResultadoLimite.permitido(sonda.getRemainingTokens());
        }

        long segundosDeEspera = Duration.ofNanos(sonda.getNanosToWaitForRefill()).toSeconds();
        return ResultadoLimite.bloqueado(Math.max(segundosDeEspera, 1));
    }

    /**
     * Descarta o balde da chave, devolvendo-lhe a cota cheia.
     *
     * <p>Serve ao limite por conta: as tentativas contam enquanto falham, e um login
     * bem-sucedido as zera. Sem isso, quem usa o sistema com frequência acabaria
     * barrado pelo próprio uso legítimo, e a trava puniria o dono da conta em vez de
     * quem a ataca.
     */
    public void reiniciar(String chave) {
        bucketsPorChave.invalidate(chave);
    }

    private Bucket construirBucket(Politica politica) {
        Bandwidth limite = Bandwidth.builder()
            .capacity(politica.tentativas())
            .refillIntervally(politica.tentativas(), politica.janela())
            .build();

        return Bucket.builder().addLimit(limite).build();
    }

    public record ResultadoLimite(boolean permitido, long tentativasRestantes, long segundosParaLiberar) {

        static ResultadoLimite permitido(long tentativasRestantes) {
            return new ResultadoLimite(true, tentativasRestantes, 0);
        }

        static ResultadoLimite bloqueado(long segundosParaLiberar) {
            return new ResultadoLimite(false, 0, segundosParaLiberar);
        }
    }
}
