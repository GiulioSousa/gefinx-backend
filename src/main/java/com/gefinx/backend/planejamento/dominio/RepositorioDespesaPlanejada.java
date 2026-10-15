package com.gefinx.backend.planejamento.dominio;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * As pendentes formam o plano; as pagas, o histórico. Uma paga não se edita nem se exclui: o
 * caminho de volta é desfazer o pagamento, que exclui a transação e a devolve às pendentes.
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

    /**
     * As pendentes que se repetem e vencem até {@code limite}, por id, travadas até o fim da
     * transação: duas leituras do plano ao mesmo tempo esperam uma pela outra, e a segunda
     * encontra a próxima que a primeira gerou.
     */
    List<DespesaPlanejada> travarQueSeRepetemAte(Long usuarioId, LocalDate limite);

    /** Se alguma ocorrência já foi gerada a partir desta. */
    boolean temSucessora(Long id);

    /** A ocorrência gerada a partir desta, paga ou não. */
    Optional<DespesaPlanejada> buscarSucessora(Long id);

    Optional<DespesaPlanejada> buscarPagaPorIdEUsuario(Long id, Long usuarioId);

    void excluir(Long id);

    /** Das transações informadas, as que pagam alguma despesa planejada do usuário. */
    Set<Long> filtrarPagamentos(Long usuarioId, Collection<Long> transacaoIds);
}
