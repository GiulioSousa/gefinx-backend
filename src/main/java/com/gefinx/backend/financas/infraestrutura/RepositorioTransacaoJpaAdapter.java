package com.gefinx.backend.financas.infraestrutura;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.Pagina;
import com.gefinx.backend.financas.dominio.Periodo;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
@Repository
public class RepositorioTransacaoJpaAdapter implements RepositorioTransacao {
    private final TransacaoSpringDataRepository springDataRepository;
    private final EntityManager entityManager;
    public RepositorioTransacaoJpaAdapter(TransacaoSpringDataRepository springDataRepository, EntityManager entityManager) {
        this.springDataRepository = springDataRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Transacao salvar(Transacao transacao) {
        TransacaoJpaEntity entidade = TransacaoJpaEntity.apartirDoDominio(transacao);
        return springDataRepository.save(entidade).paraDominio();
    }

    /**
     * Onde {@code Pageable}/{@code Page} do Spring Data viram {@link Pagina} do domínio.
     * A fronteira é aqui de propósito: a infraestrutura conhece os dois lados, o domínio
     * não precisa conhecer nenhum framework.
     *
     * <p>A ordenação vai no {@code PageRequest}, e não num {@code ORDER BY} escrito à mão,
     * porque a consulta agora é montada por {@link EspecificacoesDeTransacao}. O desempate
     * por id não é preferência: sem ele, duas transações da mesma data podem trocar de
     * posição entre uma página e a seguinte, e a mesma linha aparece duas vezes ou some das
     * duas. A ordem é exatamente a do índice criado na V9.
     */
    @Override
    public Pagina<Transacao> listarPorUsuario(Long usuarioId, FiltroDeTransacoes filtro, int pagina, int tamanho) {
        Page<TransacaoJpaEntity> resultado = springDataRepository.findAll(
            EspecificacoesDeTransacao.doUsuarioComFiltro(usuarioId, filtro),
            PageRequest.of(pagina, tamanho, Sort.by(
                Sort.Order.desc("dataTransacao"),
                Sort.Order.desc("id")
            ))
        );

        return new Pagina<>(
            resultado.getContent().stream().map(TransacaoJpaEntity::paraDominio).toList(),
            pagina,
            tamanho,
            resultado.getTotalElements()
        );
    }

    @Override
    public Optional<Transacao> buscarPorIdEUsuario(Long id, Long usuarioId) {
        return springDataRepository.findByIdAndUsuarioId(id, usuarioId).map(TransacaoJpaEntity::paraDominio);
    }

    @Override
    public void excluir(Long id) {
        springDataRepository.deleteById(id);
    }

    @Override
    public boolean existePorCategoria(Long categoriaId) {
        return springDataRepository.existsByCategoriaId(categoriaId);
    }

    @Override
    public boolean existePorConta(Long contaId) {
        return springDataRepository.existePorContaOrigemOuDestino(contaId);
    }

    @Override
    public BigDecimal somarValorPorUsuarioETipo(Long usuarioId, TipoTransacao tipo, Periodo periodo) {
        return somar(usuarioId, recorte(periodo, tipo, null), (raiz, construtor) -> raiz.get("valor"));
    }

    /**
     * O recorte por conta casa as duas pontas, como na listagem. Para receita e despesa isso
     * é o mesmo que olhar só {@code conta_id}: a CHECK da V8 garante que elas nunca têm
     * conta de destino.
     */
    @Override
    public BigDecimal somarValorPorContaETipo(Long usuarioId, Long contaId, TipoTransacao tipo, Periodo periodo) {
        return somar(usuarioId, recorte(periodo, tipo, contaId), (raiz, construtor) -> raiz.get("valor"));
    }

    /**
     * Entra positivo no destino, sai negativo na origem. O recorte garante que a conta é uma
     * das duas pontas, e a CHECK da V8 garante que nunca é as duas ao mesmo tempo — então o
     * {@code CASE} cobre todos os casos possíveis.
     */
    @Override
    public BigDecimal somarTransferenciasLiquidasDaConta(Long usuarioId, Long contaId, Periodo periodo) {
        return somar(
            usuarioId,
            recorte(periodo, TipoTransacao.TRANSFERENCIA, contaId),
            (raiz, construtor) -> construtor.<BigDecimal>selectCase()
                .when(construtor.equal(raiz.get("contaDestinoId"), contaId), raiz.<BigDecimal>get("valor"))
                .otherwise(construtor.neg(raiz.<BigDecimal>get("valor")))
        );
    }

    private static FiltroDeTransacoes recorte(Periodo periodo, TipoTransacao tipo, Long contaId) {
        return new FiltroDeTransacoes(periodo.inicio(), periodo.fim(), tipo, contaId, null);
    }

    /**
     * A soma sai da mesma {@link EspecificacoesDeTransacao} que monta a listagem, e não de um
     * JPQL próprio. Até aqui eram três {@code @Query} fixas, e bastavam porque o saldo era
     * sempre a história inteira; com o período opcional nas duas pontas, o JPQL precisaria do
     * {@code :x IS NULL OR ...} que a Etapa 21 viu quebrar no PostgreSQL e tirar a data do
     * índice. Montada assim, cada soma leva só os predicados pedidos — e o total de um período
     * é, por construção, o das linhas que a listagem desse período mostra.
     */
    private BigDecimal somar(Long usuarioId, FiltroDeTransacoes filtro, Parcela parcela) {
        CriteriaBuilder construtor = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> consulta = construtor.createQuery(BigDecimal.class);
        Root<TransacaoJpaEntity> raiz = consulta.from(TransacaoJpaEntity.class);

        consulta.select(construtor.sum(parcela.de(raiz, construtor)))
            .where(EspecificacoesDeTransacao.doUsuarioComFiltro(usuarioId, filtro).toPredicate(raiz, consulta, construtor));

        // SUM de nenhuma linha é nulo em SQL. Zero é a resposta certa para um período sem
        // lançamentos, e era o que as consultas anteriores entregavam pelo COALESCE.
        BigDecimal soma = entityManager.createQuery(consulta).getSingleResult();
        return soma == null ? BigDecimal.ZERO : soma;
    }

    /** O que cada linha do recorte contribui para a soma. */
    @FunctionalInterface
    private interface Parcela {
        Expression<BigDecimal> de(Root<TransacaoJpaEntity> raiz, CriteriaBuilder construtor);
    }

    @Override
    public List<SaldoDaConta> resumirSaldoPorConta(Long usuarioId) {
        return springDataRepository.resumirSaldoPorConta(
            usuarioId, TipoTransacao.RECEITA, TipoTransacao.DESPESA, TipoTransacao.TRANSFERENCIA
        );
    }
}
