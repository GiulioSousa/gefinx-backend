package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
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

    public DespesaPlanejadaService(RepositorioDespesaPlanejada repositorio) {
        this.repositorio = repositorio;
    }

    public List<DespesaPlanejada> listar(Long usuarioId) {
        return repositorio.listarPorUsuario(usuarioId);
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

    private DespesaPlanejada buscarOuLancar(Long usuarioId, Long id) {
        return repositorio.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoNoPlanejamentoException("Despesa planejada não encontrada"));
    }
}
