-- Desfaz a V7 (contas e transacoes.conta_id).
--
-- Rode como gefinx_migracao, que é quem tem DDL e é dono dos objetos:
--   psql -h localhost -p 5433 -U gefinx_migracao -d gefinx_db -f reverter-V7.sql
--
-- Pare a aplicação antes: com a coluna já removida e o código ainda esperando por ela,
-- toda escrita em transacoes falharia.
--
-- Existe porque a V7 é a primeira migration do projeto que altera dados já gravados, e o
-- Flyway Community não tem `undo`. Reverter uma migration destas não é operação de
-- rotina — é a saída para o caso em que o backfill se mostre errado depois de aplicado.
--
-- O QUE SE PERDE, e por que ainda assim é reversível: some a informação de qual conta
-- cada transação ocupava. Essa informação não existia antes da V7, então nada anterior a
-- ela é destruído — o saldo consolidado de cada usuário volta a ser exatamente o que era.
--
-- A EXCEÇÃO A CONFERIR ANTES DE RODAR: transações de abertura, criadas junto com contas
-- de saldo inicial depois da V7, são transações comuns e permanecem após a reversão.
-- Elas passariam a contar como receita ordinária e inflariam o saldo. Liste-as antes e
-- decida o que fazer com cada uma:
--
--   SELECT t.id, t.descricao, t.valor, t.usuario_id, c.nome AS conta
--     FROM transacoes t JOIN contas c ON c.id = t.conta_id
--     JOIN categorias g ON g.id = t.categoria_id
--    WHERE g.nome = 'Saldo inicial';

BEGIN;

ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS fk_transacao_conta_do_mesmo_usuario;
DROP INDEX IF EXISTS idx_transacoes_conta;
ALTER TABLE transacoes DROP COLUMN IF EXISTS conta_id;

DROP TABLE IF EXISTS contas;

-- Sem esta linha o Flyway acusaria a V7 como aplicada e não a rodaria de novo, deixando
-- o schema e o histórico em desacordo.
DELETE FROM flyway_schema_history WHERE version = '7';

COMMIT;
