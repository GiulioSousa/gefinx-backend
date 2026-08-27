-- Desfaz a V8 (transferência entre contas).
--
-- Rode como gefinx_migracao, que é quem tem DDL e é dono dos objetos:
--   psql -h localhost -p 5433 -U gefinx_migracao -d gefinx_db -f reverter-V8.sql
--
-- Pare a aplicação antes: com a coluna já removida e o código ainda esperando por ela,
-- toda escrita em transacoes falharia.
--
-- Diferente da reversão da V7, esta não desfaz backfill nenhum: a V8 nunca alterou dado
-- gravado, só mexeu em estrutura. Reverter é desfazer exatamente essas mudanças.
--
-- OBRIGATÓRIO ANTES DE RODAR: não pode haver nenhuma transação com tipo
-- 'TRANSFERENCIA'. Elas têm categoria_id nulo por definição e ocupam 13 caracteres na
-- coluna tipo, então tanto o SET NOT NULL quanto o estreitamento para VARCHAR(10)
-- falhariam. Para qual categoria cada transferência deveria migrar, ou se deve
-- simplesmente sumir, é decisão de negócio — não cabe a um script de reversão.
--
--   SELECT id, descricao, valor, data_transacao, conta_id, conta_destino_id
--     FROM transacoes WHERE tipo = 'TRANSFERENCIA' ORDER BY id;

BEGIN;

DO $$
DECLARE
    transferencias BIGINT;
BEGIN
    SELECT count(*) INTO transferencias FROM transacoes WHERE tipo = 'TRANSFERENCIA';
    IF transferencias > 0 THEN
        RAISE EXCEPTION
            'Ha % transferencia(s) gravada(s). Resolva-as antes de reverter a V8 - veja o cabecalho deste arquivo.',
            transferencias;
    END IF;
END $$;

ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS fk_transacao_conta_destino_do_mesmo_usuario;
DROP INDEX IF EXISTS idx_transacoes_conta_destino;
ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS ck_transferencia_contas_diferentes;
ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS ck_transferencia_tem_conta_destino;
ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS ck_transferencia_sem_categoria;

ALTER TABLE transacoes ALTER COLUMN categoria_id SET NOT NULL;

ALTER TABLE transacoes DROP CONSTRAINT IF EXISTS transacoes_tipo_check;
ALTER TABLE transacoes ADD CONSTRAINT transacoes_tipo_check
    CHECK (tipo IN ('RECEITA', 'DESPESA'));

ALTER TABLE transacoes DROP COLUMN IF EXISTS conta_destino_id;
ALTER TABLE transacoes ALTER COLUMN tipo TYPE VARCHAR(10);

DELETE FROM flyway_schema_history WHERE version = '8';

COMMIT;
