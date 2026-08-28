package com.gefinx.backend.financas.infraestrutura;

import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Monta a consulta da listagem com exatamente os filtros que vieram preenchidos.
 *
 * <p>A primeira versão desta consulta era um JPQL só, com {@code :x IS NULL OR campo = :x}
 * repetido por filtro, e ela <b>não funciona no PostgreSQL</b>: o banco recusa com
 * "could not determine data type of parameter $2", porque um parâmetro sozinho à esquerda
 * de {@code IS NULL} não tem contexto nenhum do qual o tipo possa ser inferido. Foi
 * reproduzido contra o banco de verdade — filtrar por data devolvia `500`, enquanto os
 * outros filtros passavam, o que torna a falha fácil de não notar.
 *
 * <p>Daria para calar o erro com um {@code cast} em cada parâmetro. Não é o que se quer,
 * por um motivo que vale mais do que o erro: com {@code ? IS NULL OR ...} o planejador não
 * sabe, na hora de montar o plano, se aquele filtro vai existir — então ele não pode usar
 * {@code data_transacao} do índice como faixa, e o recorte por período viraria varredura
 * das linhas do usuário. Numa etapa cujo ponto é o índice, seria contradizer o próprio
 * objetivo. Aqui cada consulta carrega só os predicados pedidos, e o plano corresponde ao
 * filtro real.
 */
final class EspecificacoesDeTransacao {

    private EspecificacoesDeTransacao() {
    }

    static Specification<TransacaoJpaEntity> doUsuarioComFiltro(Long usuarioId, FiltroDeTransacoes filtro) {
        return (raiz, consulta, construtor) -> {
            List<Predicate> predicados = new ArrayList<>();

            // Sempre primeiro, e nunca vindo de parâmetro da requisição: é ele que garante
            // o isolamento por usuário, e todo filtro abaixo só estreita o que ele já fechou.
            predicados.add(construtor.equal(raiz.get("usuarioId"), usuarioId));

            if (filtro.dataInicio() != null) {
                predicados.add(construtor.greaterThanOrEqualTo(raiz.get("dataTransacao"), filtro.dataInicio()));
            }
            if (filtro.dataFim() != null) {
                predicados.add(construtor.lessThanOrEqualTo(raiz.get("dataTransacao"), filtro.dataFim()));
            }
            if (filtro.tipo() != null) {
                predicados.add(construtor.equal(raiz.get("tipo"), filtro.tipo()));
            }
            if (filtro.categoriaId() != null) {
                predicados.add(construtor.equal(raiz.get("categoriaId"), filtro.categoriaId()));
            }
            // As duas pontas. Uma transferência é uma linha só que afeta duas contas, e ela
            // pertence ao extrato das duas — o saldo por conta já a considera nos dois lados
            // desde a Etapa 20. Comparar só a origem faria o extrato de uma conta omitir o
            // dinheiro que ela recebeu, e extrato e saldo passariam a discordar entre si.
            if (filtro.contaId() != null) {
                predicados.add(construtor.or(
                    construtor.equal(raiz.get("contaId"), filtro.contaId()),
                    construtor.equal(raiz.get("contaDestinoId"), filtro.contaId())
                ));
            }

            return construtor.and(predicados.toArray(new Predicate[0]));
        };
    }
}
