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
import com.gefinx.backend.planejamento.dominio.RepositorioEtiquetas;
import com.gefinx.backend.planejamento.dominio.excecoes.EtiquetaRecusadaException;
import com.gefinx.backend.planejamento.dominio.excecoes.PagamentoNaoDesfeitoException;
import com.gefinx.backend.planejamento.dominio.excecoes.ParcelasRecusadasException;
import com.gefinx.backend.planejamento.dominio.excecoes.RecursoNaoEncontradoNoPlanejamentoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
    private final RepositorioEtiquetas etiquetas;
    private final RegistroDePagamento registroDePagamento;
    private final FonteDeSaldo fonteDeSaldo;
    private final Clock relogio;

    public DespesaPlanejadaService(
        RepositorioDespesaPlanejada repositorio,
        RepositorioEtiquetas etiquetas,
        RegistroDePagamento registroDePagamento,
        FonteDeSaldo fonteDeSaldo,
        Clock relogio
    ) {
        this.repositorio = repositorio;
        this.etiquetas = etiquetas;
        this.registroDePagamento = registroDePagamento;
        this.fonteDeSaldo = fonteDeSaldo;
        this.relogio = relogio;
    }

    @Transactional
    public List<DespesaPlanejada> listar(Long usuarioId) {
        colocarSeriesEmDia(usuarioId);
        return repositorio.listarPendentesPorUsuario(usuarioId);
    }

    /**
     * Cada série recorrente tem no plano todas as ocorrências que vencem nos próximos
     * {@link DespesaPlanejada#DIAS_A_FRENTE} dias, e não só a atual: o aluguel semanal mostra as
     * quatro ou cinco semanas do mês, e a meta diária conta com elas. A atrasada não segura a
     * série — o aluguel de 08/10 atrasado não esconde o de 15/10, que vence de qualquer jeito.
     *
     * <p>Roda na leitura do plano, e não num agendador: é quando alguém olha que o plano precisa
     * estar certo, e um servidor desligado à meia-noite não perde nada. A trava das atrasadas
     * faz duas leituras simultâneas esperarem uma pela outra; a segunda confere de novo se a
     * próxima existe — o {@code UNIQUE} de {@code anterior_id} recusaria a duplicata, mas como
     * erro.
     */
    @Transactional
    public void colocarSeriesEmDia(Long usuarioId) {
        LocalDate hoje = LocalDate.now(relogio);
        LocalDate limite = hoje.plusDays(DespesaPlanejada.DIAS_A_FRENTE);
        for (DespesaPlanejada noPlano : repositorio.travarQueSeRepetemAte(usuarioId, limite)) {
            DespesaPlanejada ultima = noPlano;
            while (ultima.temAProximaNoPlanoEm(hoje) && !repositorio.temSucessora(ultima.getId())) {
                ultima = repositorio.salvar(ultima.proxima());
            }
        }
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
        return criar(usuarioId, descricao, valor, prazo, recorrencia, null, null, List.of());
    }

    /**
     * Com {@code parcela} e {@code totalDeParcelas}, a série acaba na última parcela. As
     * etiquetas, se vierem, têm de ser de quem cria.
     */
    @Transactional
    public DespesaPlanejada criar(
        Long usuarioId,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Recorrencia recorrencia,
        Integer parcela,
        Integer totalDeParcelas,
        List<Long> etiquetaIds
    ) {
        exigirEtiquetasDoUsuario(usuarioId, etiquetaIds);
        return repositorio.salvar(
            DespesaPlanejada.nova(descricao, valor, prazo, usuarioId, recorrencia, parcela, totalDeParcelas, etiquetaIds)
        );
    }

    public DespesaPlanejada atualizar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Recorrencia recorrencia,
        boolean serieInteira
    ) {
        return atualizar(usuarioId, id, descricao, valor, prazo, recorrencia, null, null, List.of(), serieInteira);
    }

    /**
     * Tirar a recorrência é como se encerra a série: paga, esta ocorrência não gera a seguinte.
     * As já pagas ficam como estão, no histórico.
     *
     * <p>Com {@code serieInteira}, a mudança vale também para as outras pendentes da série
     * ({@link DespesaPlanejada#editadaComo}) — o reajuste do aluguel, o vencimento que mudou de
     * dia. As pagas não mudam: o que foi pago, foi.
     *
     * <p><b>Na série inteira, as parcelas seguem a posição.</b> Esta ganha o número pedido, e as
     * outras, o dela mais a distância até ela: quem vira "3 de 6" leva a seguinte a "4 de 6". As
     * pendentes que passam da nova última saem do plano — reduzir o total de 6 para 4 tira a 5 e
     * a 6 —, mas uma paga acima do novo total recusa a edição: o que foi pago não some.
     *
     * <p>As etiquetas seguem o mesmo caminho da descrição: na série inteira, valem para todas as
     * pendentes; sem, só para esta, e a seguinte que ela gerar nasce com as dela.
     */
    @Transactional
    public DespesaPlanejada atualizar(
        Long usuarioId,
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Recorrencia recorrencia,
        Integer parcela,
        Integer totalDeParcelas,
        List<Long> etiquetaIds,
        boolean serieInteira
    ) {
        DespesaPlanejada existente = buscarOuLancar(usuarioId, id);
        exigirEtiquetasDoUsuario(usuarioId, etiquetaIds);
        DespesaPlanejada editada = existente.editada(descricao, valor, prazo, recorrencia, parcela, totalDeParcelas, etiquetaIds);
        if (serieInteira && existente.seRepete()) {
            List<DespesaPlanejada> serie = ocorrenciasDaSerie(usuarioId, existente);
            int posicao = serie.indexOf(existente);
            exigirNumeracaoPossivel(serie, posicao, editada);
            for (int indice = 0; indice < serie.size(); indice++) {
                DespesaPlanejada ocorrencia = serie.get(indice);
                if (indice == posicao || ocorrencia.estaPaga()) {
                    continue;
                }
                Integer numero = editada.eParcelada() ? editada.getParcela() + indice - posicao : null;
                if (numero != null && numero > editada.getTotalDeParcelas()) {
                    repositorio.excluir(ocorrencia.getId());
                } else {
                    repositorio.salvar(ocorrencia.editadaComo(existente, editada).comParcelas(numero, editada.getTotalDeParcelas()));
                }
            }
        }
        return repositorio.salvar(editada);
    }

    /**
     * A etiqueta de outra pessoa é recusada como a que não existe: dizer qual dos dois seria
     * contar a quem pergunta que o id existe. O banco recusaria de qualquer jeito, pela chave
     * composta da V19, mas como erro.
     */
    private void exigirEtiquetasDoUsuario(Long usuarioId, List<Long> etiquetaIds) {
        if (etiquetaIds == null) {
            return;
        }
        for (Long etiquetaId : etiquetaIds) {
            if (etiquetas.buscarPorIdEUsuario(etiquetaId, usuarioId).isEmpty()) {
                throw new EtiquetaRecusadaException("etiquetaIds", "Etiqueta não encontrada");
            }
        }
    }

    /**
     * A numeração pedida tem de caber na série: nenhuma pendente antes desta abaixo da parcela
     * 1, e nenhuma paga depois desta acima do novo total.
     */
    private static void exigirNumeracaoPossivel(List<DespesaPlanejada> serie, int posicao, DespesaPlanejada editada) {
        if (!editada.eParcelada()) {
            return;
        }
        for (int indice = 0; indice < serie.size(); indice++) {
            DespesaPlanejada ocorrencia = serie.get(indice);
            int numero = editada.getParcela() + indice - posicao;
            if (indice < posicao && !ocorrencia.estaPaga() && numero < 1) {
                throw new ParcelasRecusadasException(
                    "parcela", "Há " + (posicao - indice) + " ocorrência(s) pendente(s) antes desta: a parcela não pode ser menor que "
                        + (editada.getParcela() - numero + 1)
                );
            }
            if (indice > posicao && ocorrencia.estaPaga() && numero > editada.getTotalDeParcelas()) {
                throw new ParcelasRecusadasException(
                    "totalDeParcelas", "A parcela " + numero + " já foi paga: o total não pode ser menor que " + numero
                );
            }
        }
    }

    /**
     * Com {@code serieInteira}, saem todas as pendentes da série — as anteriores atrasadas e as
     * seguintes —, e a série acaba; as pagas ficam, no histórico. Sem, sai só esta ocorrência, e
     * a série continua: a seguinte passa a vir da anterior a esta ({@link DespesaPlanejada#ligadaA})
     * e, se esta era a última, a seguinte entra no plano no lugar dela.
     *
     * <p>Uma ocorrência que já não gera a seguinte — a que não se repete, ou a última parcela — é
     * o fim de uma série. Se a anterior ainda está pendente, a série passa a acabar nela: atrasada
     * e sem a seguinte, ela geraria de novo, na leitura seguinte, a que acabou de ser excluída.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id, boolean serieInteira) {
        DespesaPlanejada despesa = buscarOuLancar(usuarioId, id);
        if (serieInteira && despesa.seRepete()) {
            ocorrenciasDaSerie(usuarioId, despesa).stream()
                .filter(ocorrencia -> !ocorrencia.estaPaga())
                .forEach(ocorrencia -> repositorio.excluir(ocorrencia.getId()));
        } else if (!despesa.temProxima()) {
            encerrarSerieNaAnterior(usuarioId, despesa);
            repositorio.excluir(id);
        } else {
            // A exclusão vai antes: o `anterior_id` que a seguinte herda é UNIQUE, e ainda é desta.
            Optional<DespesaPlanejada> sucessora = repositorio.buscarSucessora(id);
            repositorio.excluir(id);
            repositorio.salvar(sucessora.orElseGet(despesa::proxima).ligadaA(despesa.getAnteriorId()));
        }
    }

    private void encerrarSerieNaAnterior(Long usuarioId, DespesaPlanejada despesa) {
        if (despesa.getAnteriorId() != null) {
            // A parcelada acaba virando a última parcela, e não deixando de se repetir: o número
            // que ela tem continua certo, e quem o vê sabe que é a última.
            repositorio.buscarPendentePorIdEUsuario(despesa.getAnteriorId(), usuarioId)
                .filter(DespesaPlanejada::temProxima)
                .ifPresent(anterior -> repositorio.salvar(
                    anterior.eParcelada() ? anterior.comoUltimaParcela() : anterior.semRecorrencia()
                ));
        }
    }

    /**
     * A série inteira, paga ou não, da primeira à última, seguindo {@code anterior_id} para trás
     * e para a frente. A ordem é a da corrente, e é ela que dá a posição de cada parcela.
     */
    private List<DespesaPlanejada> ocorrenciasDaSerie(Long usuarioId, DespesaPlanejada despesa) {
        List<DespesaPlanejada> serie = new ArrayList<>();
        Long anteriorId = despesa.getAnteriorId();
        while (anteriorId != null) {
            Optional<DespesaPlanejada> anterior = buscarDoUsuario(usuarioId, anteriorId);
            if (anterior.isEmpty()) {
                break;
            }
            serie.add(anterior.get());
            anteriorId = anterior.get().getAnteriorId();
        }
        Collections.reverse(serie);
        serie.add(despesa);
        Optional<DespesaPlanejada> seguinte = repositorio.buscarSucessora(despesa.getId());
        while (seguinte.isPresent()) {
            serie.add(seguinte.get());
            seguinte = repositorio.buscarSucessora(seguinte.get().getId());
        }
        return serie;
    }

    private Optional<DespesaPlanejada> buscarDoUsuario(Long usuarioId, Long id) {
        return repositorio.buscarPendentePorIdEUsuario(id, usuarioId)
            .or(() -> repositorio.buscarPagaPorIdEUsuario(id, usuarioId));
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
     * <p>Se ela tem a seguinte — se repete e não é a última parcela —, a seguinte entra no plano
     * na mesma transação, a não ser que já exista: um pagamento desfeito e refeito não gera uma
     * segunda.
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
        if (despesa.temProxima() && !repositorio.temSucessora(despesa.getId())) {
            repositorio.salvar(despesa.proxima());
        }
        return transacaoId;
    }

    /**
     * O caminho de volta do {@link #pagar}, numa transação de banco só: a transação de pagamento
     * é excluída, a despesa volta ao plano, e a próxima ocorrência que este pagamento gerou sai
     * junto — se ainda estiver como nasceu. Editada, ela fica: alguém já a ajustou, e só essa
     * pessoa sabe se ainda vale. Também fica se ela vence dentro dos dias que o plano mostra: ali a
     * despesa a teria de qualquer jeito ({@link #colocarSeriesEmDia}), e excluí-la só para gerá-la de novo
     * soltaria as que vieram depois dela.
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
        if (paga.temAProximaNoPlanoEm(LocalDate.now(relogio))) {
            return DesfechoDaProxima.MANTIDA_NO_PERIODO;
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
