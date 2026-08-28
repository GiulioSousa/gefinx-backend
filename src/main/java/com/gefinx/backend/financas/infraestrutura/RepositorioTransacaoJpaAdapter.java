package com.gefinx.backend.financas.infraestrutura;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.Pagina;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
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
    public RepositorioTransacaoJpaAdapter(TransacaoSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
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
    public BigDecimal somarValorPorUsuarioETipo(Long usuarioId, TipoTransacao tipo) {
        return springDataRepository.somarValorPorUsuarioETipo(usuarioId, tipo);
    }

    @Override
    public BigDecimal somarValorPorContaETipo(Long contaId, TipoTransacao tipo) {
        return springDataRepository.somarValorPorContaETipo(contaId, tipo);
    }

    @Override
    public BigDecimal somarTransferenciasLiquidasDaConta(Long contaId) {
        return springDataRepository.somarTransferenciasLiquidasDaConta(contaId, TipoTransacao.TRANSFERENCIA);
    }

    @Override
    public List<SaldoDaConta> resumirSaldoPorConta(Long usuarioId) {
        return springDataRepository.resumirSaldoPorConta(
            usuarioId, TipoTransacao.RECEITA, TipoTransacao.DESPESA, TipoTransacao.TRANSFERENCIA
        );
    }
}
