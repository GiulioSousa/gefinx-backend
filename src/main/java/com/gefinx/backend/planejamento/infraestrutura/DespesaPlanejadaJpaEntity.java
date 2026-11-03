package com.gefinx.backend.planejamento.infraestrutura;

import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.dominio.Recorrencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code criado_em} fica sem mapeamento, pelo motivo registrado em {@code ContaJpaEntity}. */
@Entity
@Table(name = "despesas_planejadas")
public class DespesaPlanejadaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate prazo;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "transacao_id")
    private Long transacaoId;

    @Enumerated(EnumType.STRING)
    private Recorrencia recorrencia;

    /** {@code Short} porque a coluna é {@code SMALLINT}, e o {@code validate} confere o tipo. */
    @Column(name = "dia_do_vencimento")
    private Short diaDoVencimento;

    @Column(name = "anterior_id")
    private Long anteriorId;

    /** {@code Short} pelo mesmo motivo do dia do vencimento: as colunas são {@code SMALLINT}. */
    private Short parcela;

    @Column(name = "total_de_parcelas")
    private Short totalDeParcelas;

    @Column(name = "etiqueta_id")
    private Long etiquetaId;

    protected DespesaPlanejadaJpaEntity() {
    }

    public static DespesaPlanejadaJpaEntity apartirDoDominio(DespesaPlanejada despesa) {
        DespesaPlanejadaJpaEntity entidade = new DespesaPlanejadaJpaEntity();
        entidade.id = despesa.getId();
        entidade.descricao = despesa.getDescricao();
        entidade.valor = despesa.getValor();
        entidade.prazo = despesa.getPrazo();
        entidade.usuarioId = despesa.getUsuarioId();
        entidade.transacaoId = despesa.getTransacaoId();
        entidade.recorrencia = despesa.getRecorrencia();
        entidade.diaDoVencimento = despesa.getDiaDoVencimento() == null ? null : despesa.getDiaDoVencimento().shortValue();
        entidade.anteriorId = despesa.getAnteriorId();
        entidade.parcela = paraShort(despesa.getParcela());
        entidade.totalDeParcelas = paraShort(despesa.getTotalDeParcelas());
        entidade.etiquetaId = despesa.getEtiquetaId();
        return entidade;
    }

    public DespesaPlanejada paraDominio() {
        return new DespesaPlanejada(
            id, descricao, valor, prazo, usuarioId, transacaoId, recorrencia,
            diaDoVencimento == null ? null : diaDoVencimento.intValue(), anteriorId,
            parcela == null ? null : parcela.intValue(), totalDeParcelas == null ? null : totalDeParcelas.intValue(),
            etiquetaId
        );
    }

    private static Short paraShort(Integer numero) {
        return numero == null ? null : numero.shortValue();
    }
}
