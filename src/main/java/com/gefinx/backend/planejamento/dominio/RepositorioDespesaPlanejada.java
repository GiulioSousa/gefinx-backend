package com.gefinx.backend.planejamento.dominio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * As pendentes formam o plano; as pagas, o histórico, e só são lidas para ele. Uma paga não
 * se edita nem se exclui por aqui: para desfazer o pagamento, exclui-se a transação, e a
 * despesa volta a ser pendente por conta própria.
 */
public interface RepositorioDespesaPlanejada {

    DespesaPlanejada salvar(DespesaPlanejada despesa);

    /** As pendentes, por prazo — a ordem em que o plano consome o saldo. */
    List<DespesaPlanejada> listarPendentesPorUsuario(Long usuarioId);

    Optional<DespesaPlanejada> buscarPendentePorIdEUsuario(Long id, Long usuarioId);

    /**
     * As pagas, do prazo mais recente para o mais antigo. A ordem é a do prazo, e não a da
     * data do pagamento, porque essa mora em finanças: ordenar por ela exigiria juntar os dois
     * contextos numa consulta só.
     */
    Pagina<DespesaPlanejada> listarPagasPorUsuario(Long usuarioId, int pagina, int tamanho);

    /** Se alguma ocorrência já foi gerada a partir desta. */
    boolean temSucessora(Long id);

    void excluir(Long id);

    /** Das transações informadas, as que pagam alguma despesa planejada do usuário. */
    Set<Long> filtrarPagamentos(Long usuarioId, Collection<Long> transacaoIds);
}
