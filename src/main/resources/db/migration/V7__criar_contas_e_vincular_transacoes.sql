-- Contas e carteiras.
--
-- Até aqui a transação pertencia ao usuário e a mais nada, e o saldo era um número só:
-- não havia como separar o dinheiro da conta corrente do dinheiro da carteira. Agora
-- toda transação pertence a uma conta, e a conta pertence a um usuário.
--
-- Esta é a primeira migration do projeto que altera dados já gravados. As transações
-- existentes precisam ir para alguma conta, e vão todas para uma conta padrão do próprio
-- dono. Nenhuma muda de usuário, de valor ou de tipo — então o saldo consolidado de cada
-- usuário tem de ser exatamente o mesmo antes e depois. A guarda no fim confere que o
-- backfill não deixou linha para trás nem cruzou donos; o total em si é verificado fora
-- daqui, comparando /saldo antes e depois.

CREATE TABLE contas (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(80) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_conta_nome_usuario UNIQUE (nome, usuario_id),
    -- Alvo da chave composta lá embaixo. O id sozinho já é único pela chave primária;
    -- este UNIQUE existe para tornar o par (id, usuario_id) referenciável — mesmo
    -- recurso que a V5 usou para amarrar o tipo da transação ao da categoria.
    CONSTRAINT uk_conta_id_usuario UNIQUE (id, usuario_id)
);

CREATE INDEX idx_contas_usuario ON contas(usuario_id);

-- Nulável por enquanto: a coluna precisa existir antes de haver valor para pôr nela.
ALTER TABLE transacoes ADD COLUMN conta_id BIGINT;

-- Uma conta padrão para cada usuário que já existe, inclusive os que ainda não lançaram
-- nada — assim ninguém encontra um seletor de contas vazio depois da migration. Quem se
-- cadastrar daqui em diante recebe a sua pelo RegistroUseCase, no mesmo caminho por onde
-- já nascem as categorias padrão.
INSERT INTO contas (nome, usuario_id)
SELECT 'Conta principal', id FROM usuarios;

-- O filtro pelo nome é redundante hoje, porque a tabela acabou de nascer e cada usuário
-- tem exatamente uma linha nela. Fica explícito de propósito: um UPDATE ... FROM que
-- case mais de uma conta por usuário escolhe uma em silêncio, e é o tipo de coisa que só
-- aparece quando alguém reaproveitar este trecho num banco que já tem várias contas.
UPDATE transacoes t
   SET conta_id = c.id
  FROM contas c
 WHERE c.usuario_id = t.usuario_id
   AND c.nome = 'Conta principal';

-- Duas guardas antes de travar a coluna. Um NOT NULL que estoura, ou uma chave
-- estrangeira que estoura, diriam apenas que algo não bate; estas dizem o quê.
DO $$
DECLARE
    sem_conta BIGINT;
    dono_cruzado BIGINT;
BEGIN
    SELECT count(*) INTO sem_conta FROM transacoes WHERE conta_id IS NULL;
    IF sem_conta > 0 THEN
        RAISE EXCEPTION
            'O backfill deixou % transacao(oes) sem conta. Nenhuma linha pode ficar de fora antes de a coluna virar obrigatoria.',
            sem_conta;
    END IF;

    SELECT count(*) INTO dono_cruzado
      FROM transacoes t
      JOIN contas c ON c.id = t.conta_id
     WHERE c.usuario_id <> t.usuario_id;
    IF dono_cruzado > 0 THEN
        RAISE EXCEPTION
            'O backfill ligou % transacao(oes) a conta de outro usuario.',
            dono_cruzado;
    END IF;
END $$;

ALTER TABLE transacoes ALTER COLUMN conta_id SET NOT NULL;

-- Chave composta, e não uma referência simples a contas(id): assim é o próprio banco que
-- garante que a conta de uma transação pertence ao dono dela. Com a referência simples,
-- uma transação poderia apontar para a conta de outro usuário e só a camada de aplicação
-- impediria — a mesma lacuna que a V5 fechou para o tipo da categoria.
--
-- Sem ON DELETE: excluir conta que ainda tem transação é recusado, aqui pelo banco e com
-- 409 pela aplicação. Cascatear apagaria lançamentos de anos por um clique.
ALTER TABLE transacoes ADD CONSTRAINT fk_transacao_conta_do_mesmo_usuario
    FOREIGN KEY (conta_id, usuario_id) REFERENCES contas (id, usuario_id);

CREATE INDEX idx_transacoes_conta ON transacoes(conta_id);
