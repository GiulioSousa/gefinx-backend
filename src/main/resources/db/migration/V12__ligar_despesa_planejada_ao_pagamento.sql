-- O pagamento de uma despesa planejada.
--
-- Paga é a despesa que tem uma transação ligada, e não uma coluna de estado à parte: a
-- transação é a prova do pagamento, e um "paga = sim" guardado sozinho poderia continuar
-- afirmando um pagamento que já não existe.
--
-- Por isso o SET NULL: excluir a transação de pagamento — um lançamento feito por engano,
-- por exemplo — devolve a despesa ao planejamento sozinha, sem ninguém precisar lembrar de
-- desfazer a marca. Como a V11 registra para as contas, esta é uma ligação entre contextos
-- no banco, e numa extração para serviços viraria um evento de "transação excluída".
--
-- Uma transação paga uma despesa só: o UNIQUE impede que duas apontem para o mesmo
-- lançamento, o que contaria um pagamento duas vezes.
ALTER TABLE despesas_planejadas
    ADD COLUMN transacao_id BIGINT UNIQUE REFERENCES transacoes (id) ON DELETE SET NULL;
