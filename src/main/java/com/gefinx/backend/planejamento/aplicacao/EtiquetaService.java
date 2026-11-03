package com.gefinx.backend.planejamento.aplicacao;

import com.gefinx.backend.planejamento.dominio.CorDaEtiqueta;
import com.gefinx.backend.planejamento.dominio.Etiqueta;
import com.gefinx.backend.planejamento.dominio.RepositorioEtiquetas;
import com.gefinx.backend.planejamento.dominio.excecoes.EtiquetaRecusadaException;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * As etiquetas de cada um. O nome não se repete, sem diferenciar maiúsculas: "Nubank" e
 * "nubank" seriam duas marcas que ninguém distingue no cartão.
 */
@Service
public class EtiquetaService {

    private final RepositorioEtiquetas repositorio;

    public EtiquetaService(RepositorioEtiquetas repositorio) {
        this.repositorio = repositorio;
    }

    /** {@code despesas} conta as pendentes e as pagas: é o que perde a marca se a etiqueta sair. */
    public record EtiquetaEmUso(Etiqueta etiqueta, long despesas) {
    }

    public List<EtiquetaEmUso> listar(Long usuarioId) {
        Map<Long, Long> usos = repositorio.contarDespesasPorEtiqueta(usuarioId);
        return repositorio.listarPorUsuario(usuarioId).stream()
            .map(etiqueta -> new EtiquetaEmUso(etiqueta, usos.getOrDefault(etiqueta.id(), 0L)))
            .toList();
    }

    /** Nova, ela não marca nenhuma despesa. */
    @Transactional
    public EtiquetaEmUso criar(Long usuarioId, String nome, CorDaEtiqueta cor) {
        String aparado = nome.strip();
        exigirNomeLivre(usuarioId, aparado, null);
        return new EtiquetaEmUso(repositorio.salvar(usuarioId, new Etiqueta(null, aparado, cor)), 0);
    }

    @Transactional
    public EtiquetaEmUso atualizar(Long usuarioId, Long id, String nome, CorDaEtiqueta cor) {
        repositorio.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoNoPlanejamentoException("Etiqueta não encontrada"));
        String aparado = nome.strip();
        exigirNomeLivre(usuarioId, aparado, id);
        Etiqueta salva = repositorio.salvar(usuarioId, new Etiqueta(id, aparado, cor));
        return new EtiquetaEmUso(salva, repositorio.contarDespesasPorEtiqueta(usuarioId).getOrDefault(id, 0L));
    }

    /** As despesas marcadas por ela ficam sem etiqueta, e nada mais muda nelas. */
    public void excluir(Long usuarioId, Long id) {
        if (!repositorio.excluir(usuarioId, id)) {
            throw new RecursoNaoEncontradoNoPlanejamentoException("Etiqueta não encontrada");
        }
    }

    private void exigirNomeLivre(Long usuarioId, String nome, Long excetoId) {
        if (repositorio.existeComNome(usuarioId, nome, excetoId)) {
            throw new EtiquetaRecusadaException("nome", "Já existe uma etiqueta com este nome");
        }
    }
}
