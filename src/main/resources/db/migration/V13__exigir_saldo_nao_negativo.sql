-- Saldo nunca negativo: guarda de entrada.
--
-- A regra, decidida pelo dono do projeto, é que nenhuma conta tenha saldo negativo em dia
-- algum da sua história — e, com isso, o consolidado também não, por ser a soma delas. Quem a
-- aplica daqui em diante é a aplicação, a cada lançamento (TransacaoService): uma CHECK não
-- enxerga soma acumulada entre linhas.
--
-- Esta migration não altera nada. Ela recusa subir enquanto houver conta que já esteja
-- negativa em algum dia, e diz quais: corrigir esses lançamentos é decisão de quem os fez, e
-- não de uma migration — escolher qual lançamento mudar, e para quanto, mudaria a história
-- financeira de alguém em silêncio. Depois de corrigidos, ela passa, e não roda de novo.
--
-- A conta é a mesma da aplicação, em RepositorioTransacaoJpaAdapter.primeiroDiaNegativo:
-- o saldo no fim de cada dia, acumulado desde o primeiro lançamento.
DO $$
DECLARE
    relatorio TEXT;
    quantas BIGINT;
BEGIN
    WITH por_dia AS (
        SELECT c.id AS conta_id, t.data_transacao AS dia,
               SUM(CASE WHEN t.conta_destino_id = c.id THEN t.valor
                        WHEN t.tipo = 'RECEITA' THEN t.valor
                        ELSE -t.valor END) AS movimento
          FROM contas c
          JOIN transacoes t ON t.conta_id = c.id OR t.conta_destino_id = c.id
         GROUP BY c.id, t.data_transacao
    ), acumulado AS (
        SELECT conta_id, dia, SUM(movimento) OVER (PARTITION BY conta_id ORDER BY dia) AS saldo
          FROM por_dia
    ), primeiro_estouro AS (
        SELECT DISTINCT ON (conta_id) conta_id, dia, saldo
          FROM acumulado
         WHERE saldo < 0
         ORDER BY conta_id, dia
    )
    SELECT count(*),
           string_agg(
               format('usuario %s, conta "%s" (id %s): %s em %s',
                      u.usuario, c.nome, c.id, e.saldo, to_char(e.dia, 'DD/MM/YYYY')),
               '; ' ORDER BY u.usuario, c.nome)
      INTO quantas, relatorio
      FROM primeiro_estouro e
      JOIN contas c ON c.id = e.conta_id
      JOIN usuarios u ON u.id = c.usuario_id;

    IF quantas > 0 THEN
        RAISE EXCEPTION
            'Saldo nunca negativo: % conta(s) ja ficam negativas em algum dia. Corrija os lancamentos antes de subir. Primeiro dia negativo de cada uma: %',
            quantas, relatorio;
    END IF;
END $$;
