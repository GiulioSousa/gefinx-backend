package com.gefinx.backend.planejamento.dominio;

import com.gefinx.backend.planejamento.dominio.excecoes.ParcelasRecusadasException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Um pagamento que ainda vai acontecer: quanto e até quando. Não é transação — não mexe no
 * saldo. Vira transação quando é paga, e aí sai do planejamento, para que o saldo continue
 * vindo de uma fonte só.
 *
 * <p>Paga é a que tem {@code transacaoId}: a transação é a prova do pagamento. Excluí-la
 * desfaz a ligação no banco, e a despesa volta ao planejamento.
 *
 * <p>Com {@code recorrencia}, ela é uma ocorrência de uma despesa que se repete, e paga — ou
 * dentro dos {@link #DIAS_A_FRENTE} do plano — gera a seguinte ({@link #proxima()}). {@code diaDoVencimento} é a âncora do mensal — ver a V14 — e
 * {@code anteriorId}, a ocorrência que a gerou.
 *
 * <p>A que se repete pode ter fim: {@code parcela} de {@code totalDeParcelas}, como a compra
 * parcelada no cartão ou a mensalidade de um curso. Cada ocorrência leva o seu número, a
 * seguinte nasce com o próximo, e a última não gera outra. Sem os dois, a série não acaba.
 */
public class DespesaPlanejada {

    /**
     * Quantos dias à frente o plano mostra de cada série recorrente: todas as ocorrências que
     * vencem até lá. Um mês de contas à vista, e a meta diária contando com elas.
     */
    public static final int DIAS_A_FRENTE = 30;

    private final Long id;
    private final String descricao;
    private final BigDecimal valor;
    private final LocalDate prazo;
    private final Long usuarioId;
    private final Long transacaoId;
    private final Recorrencia recorrencia;
    private final Integer diaDoVencimento;
    private final Long anteriorId;
    private final Integer parcela;
    private final Integer totalDeParcelas;

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
        this(id, descricao, valor, prazo, usuarioId, transacaoId, recorrencia, diaDoVencimento, anteriorId, null, null);
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
        Long anteriorId,
        Integer parcela,
        Integer totalDeParcelas
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
        this.parcela = parcela;
        this.totalDeParcelas = totalDeParcelas;
    }

    /** O dia do prazo vira a âncora do mensal: quem cadastra no dia 31 quer o 31. */
    public static DespesaPlanejada nova(String descricao, BigDecimal valor, LocalDate prazo, Long usuarioId, Recorrencia recorrencia) {
        return nova(descricao, valor, prazo, usuarioId, recorrencia, null, null);
    }

    /** Com {@code parcela} e {@code totalDeParcelas}, a série acaba na última: "2 de 6" vai até a 6. */
    public static DespesaPlanejada nova(
        String descricao,
        BigDecimal valor,
        LocalDate prazo,
        Long usuarioId,
        Recorrencia recorrencia,
        Integer parcela,
        Integer totalDeParcelas
    ) {
        exigirParcelasValidas(recorrencia, parcela, totalDeParcelas);
        return new DespesaPlanejada(
            null, descricao, valor, prazo, usuarioId, null, recorrencia, ancora(recorrencia, prazo), null,
            parcela, totalDeParcelas
        );
    }

    public DespesaPlanejada editada(String novaDescricao, BigDecimal novoValor, LocalDate novoPrazo, Recorrencia novaRecorrencia) {
        return editada(novaDescricao, novoValor, novoPrazo, novaRecorrencia, null, null);
    }

    /**
     * Os dados novos, mantidos a ligação com a anterior e o pagamento. A âncora só muda se o
     * prazo mudar: editar só o valor de uma ocorrência ajustada para 28/02 não pode trocar o 31
     * da série pelo 28.
     */
    public DespesaPlanejada editada(
        String novaDescricao,
        BigDecimal novoValor,
        LocalDate novoPrazo,
        Recorrencia novaRecorrencia,
        Integer novaParcela,
        Integer novoTotalDeParcelas
    ) {
        exigirParcelasValidas(novaRecorrencia, novaParcela, novoTotalDeParcelas);
        Integer novoDia = novaRecorrencia == Recorrencia.MENSAL && novoPrazo.equals(prazo) && diaDoVencimento != null
            ? diaDoVencimento
            : ancora(novaRecorrencia, novoPrazo);
        return new DespesaPlanejada(
            id, novaDescricao, novoValor, novoPrazo, usuarioId, transacaoId, novaRecorrencia, novoDia, anteriorId,
            novaParcela, novoTotalDeParcelas
        );
    }

    /**
     * Esta ocorrência com a mudança que outra da mesma série sofreu, de {@code antes} para
     * {@code depois}: descrição, valor e repetição vêm de {@code depois}. Um prazo mudado
     * desloca este do mesmo jeito — na semanal, o mesmo número de dias; na mensal, o novo dia
     * do mês, que vira a âncora e cai no último dia dos meses que não o têm.
     *
     * <p>As parcelas não vêm daqui: o número de cada ocorrência depende da posição dela na
     * série, e quem a conhece é quem percorre a série ({@link #comParcelas}). Sem repetição,
     * elas saem.
     */
    public DespesaPlanejada editadaComo(DespesaPlanejada antes, DespesaPlanejada depois) {
        LocalDate novoPrazo = prazo;
        if (!antes.prazo.equals(depois.prazo)) {
            YearMonth mes = YearMonth.from(prazo);
            novoPrazo = antes.recorrencia == Recorrencia.MENSAL
                ? mes.atDay(Math.min(depois.prazo.getDayOfMonth(), mes.lengthOfMonth()))
                : prazo.plusDays(ChronoUnit.DAYS.between(antes.prazo, depois.prazo));
        }
        Integer novoDia = depois.recorrencia != Recorrencia.MENSAL
            ? null
            : antes.recorrencia == Recorrencia.MENSAL ? depois.diaDoVencimento : novoPrazo.getDayOfMonth();
        boolean semRepeticao = depois.recorrencia == null;
        return new DespesaPlanejada(
            id, depois.descricao, depois.valor, novoPrazo, usuarioId, transacaoId, depois.recorrencia, novoDia, anteriorId,
            semRepeticao ? null : parcela, semRepeticao ? null : totalDeParcelas
        );
    }

    /** A mesma ocorrência com outra numeração — ou sem nenhuma, com os dois nulos. */
    public DespesaPlanejada comParcelas(Integer novaParcela, Integer novoTotalDeParcelas) {
        exigirParcelasValidas(recorrencia, novaParcela, novoTotalDeParcelas);
        return new DespesaPlanejada(
            id, descricao, valor, prazo, usuarioId, transacaoId, recorrencia, diaDoVencimento, anteriorId,
            novaParcela, novoTotalDeParcelas
        );
    }

    /**
     * A série termina nesta: a parcela vira a última. É como se encerra uma parcelada quando a
     * seguinte é excluída sozinha — sem isso, ela nasceria de novo na leitura seguinte.
     */
    public DespesaPlanejada comoUltimaParcela() {
        return comParcelas(parcela, parcela);
    }

    public DespesaPlanejada pagaCom(Long novaTransacaoId) {
        return new DespesaPlanejada(
            id, descricao, valor, prazo, usuarioId, novaTransacaoId, recorrencia, diaDoVencimento, anteriorId,
            parcela, totalDeParcelas
        );
    }

    /**
     * A ocorrência seguinte: mesma descrição, mesmo valor — que se edita se a conta vier
     * diferente —, e o prazo um período adiante. Na parcelada, com o número seguinte. Só existe
     * para a que se repete e não é a última parcela.
     */
    public DespesaPlanejada proxima() {
        if (!temProxima()) {
            throw new IllegalStateException("Esta ocorrência não tem seguinte: não se repete, ou é a última parcela");
        }
        return new DespesaPlanejada(
            null, descricao, valor, recorrencia.proximoPrazo(prazo, diaDoVencimento), usuarioId, null,
            recorrencia, diaDoVencimento, id, parcela == null ? null : parcela + 1, totalDeParcelas
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
            id, descricao, valor, prazo, usuarioId, transacaoId, recorrencia, diaDoVencimento, novoAnteriorId,
            parcela, totalDeParcelas
        );
    }

    /** A série acaba nesta: nem paga, nem atrasada, ela gera a seguinte. */
    public DespesaPlanejada semRecorrencia() {
        return new DespesaPlanejada(id, descricao, valor, prazo, usuarioId, transacaoId, null, null, anteriorId);
    }

    /**
     * Se o plano a mostra, contando de {@code hoje}: vence até {@link #DIAS_A_FRENTE} dias
     * adiante — as atrasadas inclusive.
     */
    public boolean entraNoPlanoEm(LocalDate hoje) {
        return !prazo.isAfter(hoje.plusDays(DIAS_A_FRENTE));
    }

    /** Se a ocorrência seguinte a esta já tem lugar no plano de {@code hoje}. */
    public boolean temAProximaNoPlanoEm(LocalDate hoje) {
        return temProxima() && proxima().entraNoPlanoEm(hoje);
    }

    /**
     * Se {@code sucessora} está como {@link #proxima()} a gerou: mesma descrição, valor, prazo,
     * repetição e parcela. É o que separa a próxima que se pode descartar ao desfazer este
     * pagamento da que alguém já ajustou — o valor da conta do mês, por exemplo — e que deve
     * ficar.
     */
    public boolean gerouSemMudancas(DespesaPlanejada sucessora) {
        if (!temProxima()) {
            return false;
        }
        DespesaPlanejada esperada = proxima();
        return sucessora.descricao.equals(esperada.descricao)
            && sucessora.valor.compareTo(esperada.valor) == 0
            && sucessora.prazo.equals(esperada.prazo)
            && sucessora.recorrencia == esperada.recorrencia
            && Objects.equals(sucessora.parcela, esperada.parcela)
            && Objects.equals(sucessora.totalDeParcelas, esperada.totalDeParcelas);
    }

    public boolean estaPaga() {
        return transacaoId != null;
    }

    public boolean seRepete() {
        return recorrencia != null;
    }

    /** Se gera a ocorrência seguinte: se repete, e não é a última parcela. */
    public boolean temProxima() {
        return seRepete() && (totalDeParcelas == null || parcela < totalDeParcelas);
    }

    public boolean eParcelada() {
        return totalDeParcelas != null;
    }

    private static Integer ancora(Recorrencia recorrencia, LocalDate prazo) {
        return recorrencia == Recorrencia.MENSAL ? prazo.getDayOfMonth() : null;
    }

    /**
     * Os dois juntos ou nenhum, só na que se repete, e a parcela dentro do total. Os limites de
     * cada número sozinho — de 1 a 999 — ficam na borda; aqui fica o que depende de mais de um
     * campo.
     */
    private static void exigirParcelasValidas(Recorrencia recorrencia, Integer parcela, Integer totalDeParcelas) {
        if (parcela == null && totalDeParcelas == null) {
            return;
        }
        if (parcela == null || totalDeParcelas == null) {
            throw new ParcelasRecusadasException(
                parcela == null ? "parcela" : "totalDeParcelas",
                "Informe a parcela e o total de parcelas, ou nenhum dos dois"
            );
        }
        if (recorrencia == null) {
            throw new ParcelasRecusadasException("recorrencia", "Só a despesa que se repete tem parcelas");
        }
        if (parcela > totalDeParcelas) {
            throw new ParcelasRecusadasException("parcela", "A parcela não pode passar do total de parcelas");
        }
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

    public Integer getParcela() {
        return parcela;
    }

    public Integer getTotalDeParcelas() {
        return totalDeParcelas;
    }
}
