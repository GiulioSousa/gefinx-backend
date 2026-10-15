package com.gefinx.backend.planejamento.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Um pagamento que ainda vai acontecer: quanto e até quando. Não é transação — não mexe no
 * saldo. Vira transação quando é paga, e aí sai do planejamento, para que o saldo continue
 * vindo de uma fonte só.
 *
 * <p>Paga é a que tem {@code transacaoId}: a transação é a prova do pagamento. Excluí-la
 * desfaz a ligação no banco, e a despesa volta ao planejamento.
 *
 * <p>Com {@code recorrencia}, ela é uma ocorrência de uma despesa que se repete, e paga — ou
 * atrasada — gera a seguinte ({@link #proxima()}). {@code diaDoVencimento} é a âncora do mensal — ver a V14 — e
 * {@code anteriorId}, a ocorrência que a gerou.
 */
public class DespesaPlanejada {

    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final LocalDate prazo;
    private final Long usuarioId;
    private final Long transacaoId;
    private final Recorrencia recorrencia;
    private final Integer diaDoVencimento;
    private final Long anteriorId;

    public DespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId) {
        this(id, descricao, valor, prazo, usuarioId, null);
    }

    public DespesaPlanejada(Long id, String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId, Long transacaoId) {
        this(id, descricao, valor, prazo, usuarioId, transacaoId, null, null, null);
    }

    public DespesaPlanejada(
        Long id,
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Long usuarioId,
        Long transacaoId,
        Recorrencia recorrencia,
        Integer diaDoVencimento,
        Long anteriorId
    ) {
        this.id = id;
        this.descricao = descricao;
        this.valor = valor;
        this.prazo = prazo;
        this.usuarioId = usuarioId;
        this.transacaoId = transacaoId;
        this.recorrencia = recorrencia;
        this.diaDoVencimento = diaDoVencimento;
        this.anteriorId = anteriorId;
    }

    /** O dia do prazo vira a âncora do mensal: quem cadastra no dia 31 quer o 31. */
    public static DespesaPlanejada nova(String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId, Recorrencia recorrencia) {
        return new DespesaPlanejada(null, descricao, valor, prazo, usuarioId, null, recorrencia, ancora(recorrencia, prazo), null);
    }

    /**
     * Os dados novos, mantidos a ligação com a anterior e o pagamento. A âncora só muda se o
     * prazo mudar: editar só o valor de uma ocorrência ajustada para 28/02 não pode trocar o 31
     * da série pelo 28.
     */
    public DespesaPlanejada editada(String novaDescricao, BigDecimal novoValor, LocalDate novoPrazo, Recorrencia novaRecorrencia) {
        Integer novoDia = novaRecorrencia == Recorrencia.MENSAL && novoPrazo.equals(prazo) && diaDoVencimento != null
            ? diaDoVencimento
            : ancora(novaRecorrencia, novoPrazo);
        return new DespesaPlanejada(
            id, novaDescricao, novoValor, novoPrazo, usuarioId, transacaoId, novaRecorrencia, novoDia, anteriorId
        );
    }

    public DespesaPlanejada pagaCom(Long novaTransacaoId) {
        return new DespesaPlanejada(
            id, descricao, valor, prazo, usuarioId, novaTransacaoId, recorrencia, diaDoVencimento, anteriorId
        );
    }

    /**
     * A ocorrência seguinte: mesma descrição, mesmo valor — que se edita se a conta vier
     * diferente —, e o prazo um período adiante. Só existe para a que se repete.
     */
    public DespesaPlanejada proxima() {
        if (recorrencia == null) {
            throw new IllegalStateException("Despesa que não se repete não tem próxima ocorrência");
        }
        return new DespesaPlanejada(
            null, descricao, valor, recorrencia.proximoPrazo(prazo, diaDoVencimento), usuarioId, null,
            recorrencia, diaDoVencimento, id
        );
    }

    /** De volta às pendentes, com tudo o mais como estava. */
    public DespesaPlanejada semPagamento() {
        return pagaCom(null);
    }

    /**
     * A mesma ocorrência, presa a outra anterior. É como a série pula uma ocorrência excluída: a
     * seguinte passa a vir da anterior dela, e a anterior não fica sem sucessora — o que a faria
     * gerar de novo a que saiu.
     */
    public DespesaPlanejada ligadaA(Long novoAnteriorId) {
        return new DespesaPlanejada(
            id, descricao, valor, prazo, usuarioId, transacaoId, recorrencia, diaDoVencimento, novoAnteriorId
        );
    }

    /** A série acaba nesta: nem paga, nem atrasada, ela gera a seguinte. */
    public DespesaPlanejada semRecorrencia() {
        return new DespesaPlanejada(id, descricao, valor, prazo, usuarioId, transacaoId, null, null, anteriorId);
    }

    /** Mesma regra da situação {@code ATRASADA} do plano: o dia do prazo ainda está em dia. */
    public boolean estaAtrasadaEm(LocalDate hoje) {
        return prazo.isBefore(hoje);
    }

    /**
     * Se {@code sucessora} está como {@link #proxima()} a gerou: mesma descrição, valor, prazo e
     * repetição. É o que separa a próxima que se pode descartar ao desfazer este pagamento da
     * que alguém já ajustou — o valor da conta do mês, por exemplo — e que deve ficar.
     */
    public boolean gerouSemMudancas(DespesaPlanejada sucessora) {
        if (!seRepete()) {
            return false;
        }
        DespesaPlanejada esperada = proxima();
        return sucessora.descricao.equals(esperada.descricao)
            && sucessora.valor.compareTo(esperada.valor) == 0
            && sucessora.prazo.equals(esperada.prazo)
            && sucessora.recorrencia == esperada.recorrencia;
    }

    public boolean estaPaga() {
        return transacaoId != null;
    }

    public boolean seRepete() {
        return recorrencia != null;
    }

    private static Integer ancora(Recorrencia recorrencia, LocalDate prazo) {
        return recorrencia == Recorrencia.MENSAL ? prazo.getDayOfMonth() : null;
    }

    public Long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getTransacaoId() {
        return transacaoId;
    }

    public Recorrencia getRecorrencia() {
        return recorrencia;
    }

    public Integer getDiaDoVencimento() {
        return diaDoVencimento;
    }

    public Long getAnteriorId() {
        return anteriorId;
    }
}
