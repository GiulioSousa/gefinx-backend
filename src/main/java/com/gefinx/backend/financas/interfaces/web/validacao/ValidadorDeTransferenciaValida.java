package com.gefinx.backend.financas.interfaces.web.validacao;

import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoTransacao;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

/**
 * Transferência e lançamento comum são formatos opostos e exclusivos: um tem categoria e
 * não tem conta de destino, o outro é exatamente o inverso. As mesmas regras estão nas
 * CHECKs da V8 — aqui elas existem para que a recusa chegue ao cliente como `400` no
 * campo que falhou, e não como erro de banco.
 */
public class ValidadorDeTransferenciaValida
    implements ConstraintValidator<TransferenciaValida, RequisicaoTransacao> {

    @Override
    public boolean isValid(RequisicaoTransacao requisicao, ConstraintValidatorContext contexto) {
        // Tipo ausente é falha do @NotNull do próprio campo. Checar aqui também renderia
        // duas mensagens para a mesma falha.
        if (requisicao == null || requisicao.tipo() == null) {
            return true;
        }

        if (requisicao.tipo() == TipoTransacao.TRANSFERENCIA) {
            return validarTransferencia(requisicao, contexto);
        }
        return validarLancamentoComum(requisicao, contexto);
    }

    private boolean validarTransferencia(RequisicaoTransacao requisicao, ConstraintValidatorContext contexto) {
        if (requisicao.categoriaId() != null) {
            return recusar(contexto, "categoriaId",
                "Categoria não se aplica a uma transferência: o dinheiro muda de conta, não de destinação");
        }

        if (requisicao.contaDestinoId() == null) {
            return recusar(contexto, "contaDestinoId", "A conta de destino é obrigatória");
        }

        if (Objects.equals(requisicao.contaDestinoId(), requisicao.contaId())) {
            return recusar(contexto, "contaDestinoId",
                "A conta de destino deve ser diferente da conta de origem");
        }

        return true;
    }

    private boolean validarLancamentoComum(RequisicaoTransacao requisicao, ConstraintValidatorContext contexto) {
        if (requisicao.categoriaId() == null) {
            return recusar(contexto, "categoriaId", "A categoria é obrigatória");
        }

        if (requisicao.contaDestinoId() != null) {
            return recusar(contexto, "contaDestinoId",
                "A conta de destino só se aplica a transferências");
        }

        return true;
    }

    /**
     * O {@code addPropertyNode} é o que faz a violação virar erro <b>de campo</b>. Sem
     * ele, a anotação é de classe e a violação sai sem campo associado — e o
     * {@code ManipuladorGlobalDeExcecoes} só lê {@code getFieldErrors()}, de modo que a
     * mensagem sumiria e o cliente receberia "Dados inválidos" com o mapa de erros vazio,
     * desfazendo em silêncio o que a Etapa 16 construiu.
     */
    private boolean recusar(ConstraintValidatorContext contexto, String campo, String mensagem) {
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensagem)
            .addPropertyNode(campo)
            .addConstraintViolation();
        return false;
    }
}
