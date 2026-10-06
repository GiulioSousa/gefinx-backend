package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DesfechoDaProxima;
import com.gefinx.backend.planejamento.dominio.DespesaPaga;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.FonteDeSaldo;
import com.gefinx.backend.planejamento.dominio.PagamentoLancado;
import com.gefinx.backend.planejamento.dominio.Pagina;
import com.gefinx.backend.planejamento.dominio.Recorrencia;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.PagamentoNaoDesfeitoException;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aceita prazo no passado, de propósito. Recusá-lo na criação e aceitá-lo na edição seria
 * incoerente — uma despesa que venceu sem ser paga continua sendo editada —, e recusar nas
 * duas impediria corrigir a descrição de uma despesa atrasada. Ela entra no plano como
 * atrasada, que é o aviso certo.
 */
@Service
public class DespesaPlanejadaService {

    private final RepositorioDespesaPlanejada repositorio;
    private final RegistroDePagamento registroDePagamento;
    private final FonteDeSaldo fonteDeSaldo;

    public DespesaPlanejadaService(
        RepositorioDespesaPlanejada repositorio,
        RegistroDePagamento registroDePagamento,
        FonteDeSaldo fonteDeSaldo
    ) {
        this.repositorio = repositorio;
        this.registroDePagamento = registroDePagamento;
        this.fonteDeSaldo = fonteDeSaldo;
    }

    public List<DespesaPlanejada> listar(Long usuarioId) {
        return repositorio.listarPendentesPorUsuario(usuarioId);
    }

    /**
     * O histórico: uma página de pagas, cada uma com a transação que a pagou. As transações da
     * página vêm de finanças numa leitura só, e não uma por despesa.
     */
    public Pagina<DespesaPaga> listarPagas(Long usuarioId, int pagina, int tamanho) {
        Pagina<DespesaPlanejada> pagas = repositorio.listarPagasPorUsuario(usuarioId, pagina, tamanho);
        List<Long> transacaoIds = pagas.itens().stream().map(DespesaPlanejada::getTransacaoId).toList();
        Map<Long, PagamentoLancado> pagamentos = fonteDeSaldo.pagamentos(usuarioId, transacaoIds).stream()
            .collect(Collectors.toMap(PagamentoLancado::transacaoId, Function.identity()));

        List<DespesaPaga> itens = pagas.itens().stream()
            .map(despesa -> new DespesaPaga(despesa, Optional.ofNullable(pagamentos.get(despesa.getTransacaoId()))))
            .toList();
        return new Pagina<>(itens, pagas.pagina(), pagas.tamanho(), pagas.totalItens());
    }

    @Transactional
    public DespesaPlanejada criar(Long usuarioId, String descricao, BigDecimal valor, LocalDate prazo, Recorrencia recorrencia) {
        return repositorio.salvar(DespesaPlanejada.nova(descricao, valor, prazo, usuarioId, recorrencia));
    }

    /**
     * Tirar a recorrência é como se encerra a série: paga, esta ocorrência não gera a seguinte.
     * As já pagas ficam como estão, no histórico.
     */
    @Transactional
    public DespesaPlanejada atualizar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Recorrencia recorrencia
    ) {
        DespesaPlanejada existente = buscarOuLancar(usuarioId, id);
        return repositorio.salvar(existente.editada(descricao, valor, prazo, recorrencia));
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        buscarOuLancar(usuarioId, id);
        repositorio.excluir(id);
    }

    /**
     * Lança a despesa em finanças e liga a planejada a ela, numa transação de banco só. Feito
     * pelo cliente em duas chamadas, uma falha entre elas deixaria o valor contado duas vezes:
     * no saldo, que já caiu, e na lista, onde a despesa continuaria pendente.
     *
     * <p>Uma despesa já paga não é encontrada, e pagar de novo dá {@code 404}: a busca só enxerga
     * as pendentes. Valor e data vêm de quem paga, e não da despesa — paga-se às vezes um
     * pouco a mais ou a menos do que se planejou, e em outro dia.
     *
     * <p>Se ela se repete, a ocorrência seguinte entra no plano na mesma transação — a não ser
     * que já exista: um pagamento desfeito e refeito não gera uma segunda.
     *
     * @return o id da transação criada
     */
    @Transactional
    public Long pagar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate data,
        Long categoriaId,
        Long contaId
    ) {
        DespesaPlanejada despesa = buscarOuLancar(usuarioId, id);
        Long transacaoId = registroDePagamento.registrar(usuarioId, descricao, valor, data, categoriaId, contaId);
        repositorio.salvar(despesa.pagaCom(transacaoId));
        if (despesa.seRepete() && !repositorio.temSucessora(despesa.getId())) {
            repositorio.salvar(despesa.proxima());
        }
        return transacaoId;
    }

    /**
     * O caminho de volta do {@link #pagar}, numa transação de banco só: a transação de pagamento
     * é excluída, a despesa volta ao plano, e a próxima ocorrência que este pagamento gerou sai
     * junto — se ainda estiver como nasceu. Editada, ela fica: alguém já a ajustou, e só essa
     * pessoa sabe se ainda vale.
     *
     * <p>Se a próxima já foi paga, nada muda: desfazer só esta deixaria duas ocorrências da
     * mesma série com o calendário fora de ordem. Desfaz-se primeiro a mais recente.
     *
     * <p>A despesa é gravada sem o pagamento antes de a transação ser excluída. O banco limparia
     * a ligação sozinho ({@code ON DELETE SET NULL}), mas a cópia em memória ainda apontaria para
     * a transação apagada, e uma gravação posterior a restauraria.
     */
    @Transactional
    public DesfechoDaProxima desfazerPagamento(Long usuarioId, Long id) {
        DespesaPlanejada paga = repositorio.buscarPagaPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoNoPlanejamentoException("Despesa paga não encontrada"));
        Optional<DespesaPlanejada> sucessora = repositorio.buscarSucessora(id);
        if (sucessora.isPresent() && sucessora.get().estaPaga()) {
            throw new PagamentoNaoDesfeitoException(
                "A próxima ocorrência de \"" + paga.getDescricao() + "\" já foi paga. Desfaça primeiro o pagamento dela."
            );
        }

        repositorio.salvar(paga.semPagamento());
        registroDePagamento.desfazer(usuarioId, paga.getTransacaoId());

        if (sucessora.isEmpty()) {
            return DesfechoDaProxima.NENHUMA;
        }
        if (paga.gerouSemMudancas(sucessora.get())) {
            repositorio.excluir(sucessora.get().getId());
            return DesfechoDaProxima.REMOVIDA;
        }
        return DesfechoDaProxima.MANTIDA;
    }

    private DespesaPlanejada buscarOuLancar(Long usuarioId, Long id) {
        return repositorio.buscarPendentePorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoNoPlanejamentoException("Despesa planejada não encontrada"));
    }
}
