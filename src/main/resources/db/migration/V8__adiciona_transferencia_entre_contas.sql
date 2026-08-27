-- Transferência entre contas.
--
-- Diferente da V7, esta migration não altera dado nenhum já gravado: alarga uma coluna,
-- adiciona outra, relaxa uma NOT NULL e acrescenta CHECKs que toda linha existente já
-- satisfaz — hoje tipo é sempre RECEITA ou DESPESA, então
-- (tipo = 'TRANSFERENCIA') = (categoria_id IS NULL) reduz a false = false em qualquer
-- linha atual.
--
-- Por que a CHECK não enfraquece a amarração da V5. A chave estrangeira composta
-- fk_transacao_categoria_do_mesmo_tipo (categoria_id, tipo) usa MATCH SIMPLE, o padrão
-- do PostgreSQL: quando qualquer coluna da FK é nula, a restrição inteira é considerada
-- satisfeita e não é verificada. Sem mais nada, uma linha com categoria_id nulo e
-- tipo = 'DESPESA' passaria despercebida pela FK. A CHECK abaixo fecha exatamente essa
-- brecha, particionando a tabela sem sobra e sem lacuna: toda linha RECEITA/DESPESA cai
-- sob a FK (categoria_id preenchido, checada normalmente); toda linha TRANSFERENCIA cai
-- sob a CHECK (categoria_id nulo, FK pulada de propósito, CHECK garante que só aconteceu
-- porque é transferência). As duas regras juntas cobrem cada linha, uma vez cada.

-- 'TRANSFERENCIA' tem 13 caracteres e a coluna comportava 10, dimensionada quando só
-- havia 'RECEITA' e 'DESPESA'. Sem este passo a migration aplica limpa e a primeira
-- transferência falha na escrita, longe daqui. Foi o ensaio contra o banco com dados que
-- mostrou isso — aplicar a V8 num banco vazio não teria revelado nada.
--
-- categorias.tipo fica em VARCHAR(10) de propósito: categoria nunca é TRANSFERENCIA, e
-- manter a coluna estreita significa que o valor não cabe lá nem por engano de código.
-- A FK composta continua valendo entre colunas de larguras diferentes — varchar(13) e
-- varchar(10) são o mesmo tipo, e o comprimento é restrição, não tipo distinto.
ALTER TABLE transacoes ALTER COLUMN tipo TYPE VARCHAR(13);

ALTER TABLE transacoes ADD COLUMN conta_destino_id BIGINT NULL;

ALTER TABLE transacoes DROP CONSTRAINT transacoes_tipo_check;
ALTER TABLE transacoes ADD CONSTRAINT transacoes_tipo_check
    CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA'));

ALTER TABLE transacoes ALTER COLUMN categoria_id DROP NOT NULL;

ALTER TABLE transacoes ADD CONSTRAINT ck_transferencia_sem_categoria
    CHECK ((tipo = 'TRANSFERENCIA') = (categoria_id IS NULL));

ALTER TABLE transacoes ADD CONSTRAINT ck_transferencia_tem_conta_destino
    CHECK ((tipo = 'TRANSFERENCIA') = (conta_destino_id IS NOT NULL));

-- IS DISTINCT FROM trata NULL corretamente: linhas que não são transferência têm
-- conta_destino_id nulo e passam sempre, sem precisar de um OR conta_destino_id IS NULL
-- ao lado.
ALTER TABLE transacoes ADD CONSTRAINT ck_transferencia_contas_diferentes
    CHECK (conta_destino_id IS DISTINCT FROM conta_id);

-- Mesma garantia que conta_id já tem: o destino só pode ser conta do próprio usuário.
-- Nulável, então MATCH SIMPLE deixa passar toda linha que não é transferência.
ALTER TABLE transacoes ADD CONSTRAINT fk_transacao_conta_destino_do_mesmo_usuario
    FOREIGN KEY (conta_destino_id, usuario_id) REFERENCES contas (id, usuario_id);

CREATE INDEX idx_transacoes_conta_destino ON transacoes(conta_destino_id);
