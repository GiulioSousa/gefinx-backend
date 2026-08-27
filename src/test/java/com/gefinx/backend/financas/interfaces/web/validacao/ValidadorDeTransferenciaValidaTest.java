package com.gefinx.backend.financas.interfaces.web.validacao;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoTransacao;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transferência e lançamento comum são formatos opostos, e cada recusa precisa chegar
 * <b>no campo que falhou</b>.
 *
 * <p>Os testes leem o mapa campo → mensagem montado do mesmo jeito que o
 * {@code ManipuladorGlobalDeExcecoes} monta — por {@code getPropertyPath()}. Uma anotação
 * de classe que esquecesse o {@code addPropertyNode} continuaria recusando a requisição,
 * mas sem campo associado: o cliente receberia "Dados inválidos" com o mapa vazio, e a
 * garantia da Etapa 16 se desfaria em silêncio. Um teste que olhasse só a mensagem não
 * pegaria isso.
 */
class ValidadorDeTransferenciaValidaTest {

    private static final Long CONTA_ORIGEM = 1L;
    private static final Long CONTA_DESTINO = 2L;
    private static final Long CATEGORIA = 10L;
    private static final LocalDate DATA = LocalDate.of(2026, 8, 24);

    private static Validator validador;

    @BeforeAll
    static void prepararValidador() {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            validador = fabrica.getValidator();
        }
    }

    @Test
    void aceitaTransferenciaBemFormada() {
        assertThat(erros(TipoTransacao.TRANSFERENCIA, null, CONTA_ORIGEM, CONTA_DESTINO)).isEmpty();
    }

    @Test
    void aceitaLancamentoComumBemFormado() {
        assertThat(erros(TipoTransacao.DESPESA, CATEGORIA, CONTA_ORIGEM, null)).isEmpty();
    }

    @Test
    void recusaTransferenciaComCategoria() {
        assertThat(erros(TipoTransacao.TRANSFERENCIA, CATEGORIA, CONTA_ORIGEM, CONTA_DESTINO))
            .containsKey("categoriaId");
    }

    @Test
    void recusaTransferenciaSemContaDeDestino() {
        assertThat(erros(TipoTransacao.TRANSFERENCIA, null, CONTA_ORIGEM, null))
            .containsEntry("contaDestinoId", "A conta de destino é obrigatória");
    }

    @Test
    void recusaTransferenciaParaAPropriaConta() {
        assertThat(erros(TipoTransacao.TRANSFERENCIA, null, CONTA_ORIGEM, CONTA_ORIGEM))
            .containsEntry("contaDestinoId", "A conta de destino deve ser diferente da conta de origem");
    }

    @Test
    void recusaLancamentoComumSemCategoria() {
        assertThat(erros(TipoTransacao.DESPESA, null, CONTA_ORIGEM, null))
            .containsEntry("categoriaId", "A categoria é obrigatória");
    }

    @Test
    void recusaLancamentoComumComContaDeDestino() {
        assertThat(erros(TipoTransacao.RECEITA, CATEGORIA, CONTA_ORIGEM, CONTA_DESTINO))
            .containsEntry("contaDestinoId", "A conta de destino só se aplica a transferências");
    }

    /** Tipo ausente é falha do {@code @NotNull}; o validador não pode duplicar a mensagem. */
    @Test
    void deixaOTipoAusenteParaANotNullDoCampo() {
        Map<String, String> erros = erros(null, null, CONTA_ORIGEM, null);

        assertThat(erros).containsEntry("tipo", "O tipo é obrigatório");
        assertThat(erros).doesNotContainKeys("categoriaId", "contaDestinoId");
    }

    private Map<String, String> erros(TipoTransacao tipo, Long categoriaId, Long contaId, Long contaDestinoId) {
        RequisicaoTransacao requisicao = new RequisicaoTransacao(
            "Movimento qualquer", new BigDecimal("10.00"), tipo, categoriaId, contaId, contaDestinoId, DATA
        );

        Map<String, String> porCampo = new LinkedHashMap<>();
        for (ConstraintViolation<RequisicaoTransacao> violacao : validador.validate(requisicao)) {
            porCampo.put(violacao.getPropertyPath().toString(), violacao.getMessage());
        }
        return porCampo;
    }
}
