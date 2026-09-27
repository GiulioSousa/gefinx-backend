package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.RegistroDePagamento;
import com.gefinx.backend.planejamento.dominio.RepositorioDespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    public DespesaPlanejadaService(RepositorioDespesaPlanejada repositorio, RegistroDePagamento registroDePagamento) {
        this.repositorio = repositorio;
        this.registroDePagamento = registroDePagamento;
    }

    public List<DespesaPlanejada> listar(Long usuarioId) {
        return repositorio.listarPendentesPorUsuario(usuarioId);
    }

    @Transactional
    public DespesaPlanejada criar(Long usuarioId, String descricao, BigDecimal valor, LocalDate prazo) {
        return repositorio.salvar(DespesaPlanejada.nova(descricao, valor, prazo, usuarioId));
    }

    @Transactional
    public DespesaPlanejada atualizar(Long usuarioId, Long id, String descricao, BigDecimal valor, LocalDate prazo) {
        buscarOuLancar(usuarioId, id);
        return repositorio.salvar(new DespesaPlanejada(id, descricao, valor, prazo, usuarioId));
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
        return transacaoId;
    }

    private DespesaPlanejada buscarOuLancar(Long usuarioId, Long id) {
        return repositorio.buscarPendentePorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoNoPlanejamentoException("Despesa planejada não encontrada"));
    }
}
