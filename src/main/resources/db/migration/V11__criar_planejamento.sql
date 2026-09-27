-- Planejamento: despesas que ainda vão ser pagas, e as contas que ficam fora da conta.
--
-- Nada aqui mexe em dado já gravado de transações ou contas. Despesa planejada não é
-- transação e não entra no saldo: vira transação quando é paga.

CREATE TABLE despesas_planejadas (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(200) NOT NULL,
    -- O mesmo tipo e a mesma regra de `transacoes.valor`: é numa transação que ela vai
    -- parar quando for paga, e um valor que coubesse aqui e não lá travaria o pagamento.
    valor NUMERIC(14, 2) NOT NULL CHECK (valor > 0),
    prazo DATE NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    criado_em TIMESTAMP NOT NULL DEFAULT now()
);

-- A leitura é sempre "as do usuário, por prazo" — a ordem em que o plano consome o saldo.
CREATE INDEX idx_despesas_planejadas_usuario_prazo ON despesas_planejadas (usuario_id, prazo);

-- Contas cujo dinheiro não conta para o planejamento, como a carteira de dinheiro vivo.
-- Guardar as de fora, e não as de dentro, faz a conta nova entrar sozinha: é o caso comum,
-- e esquecer de marcá-la deixaria dinheiro de verdade fora do plano sem aviso.
--
-- A chave composta amarra a linha ao dono da conta, pelo mesmo motivo da V7: com
-- referência simples a `contas(id)`, o banco aceitaria marcar a conta de outra pessoa.
-- É a única ligação deste contexto com o de finanças no banco — numa extração para
-- serviços ela viraria um evento de "conta excluída". Até lá, o CASCADE faz a exclusão de
-- uma conta levar a marca junto, em vez de ser barrada por ela.
CREATE TABLE contas_fora_do_planejamento (
    conta_id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_conta_fora_do_planejamento FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE CASCADE
);

CREATE INDEX idx_contas_fora_do_planejamento_usuario ON contas_fora_do_planejamento (usuario_id);

-- Ponto de partida pedido pelo dono do projeto: o dinheiro em espécie fica fora. É uma
-- semente, e só roda uma vez — daqui em diante quem decide é a tela de Planejamento, pelo
-- id da conta, e renomear a conta não muda nada. Quem não tem conta com esse nome
-- simplesmente não recebe linha.
INSERT INTO contas_fora_do_planejamento (conta_id, usuario_id)
SELECT id, usuario_id
  FROM contas
 WHERE lower(btrim(nome)) = 'em espécie';
