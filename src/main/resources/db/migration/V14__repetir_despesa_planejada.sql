-- Despesa planejada que se repete: toda semana ou todo mês.
--
-- Cada ocorrência é uma linha própria de despesas_planejadas, e não uma regra à parte que gera
-- linhas: o plano e o histórico continuam lendo uma despesa de cada vez, com o seu valor e o
-- seu prazo. A próxima ocorrência nasce quando a atual é paga, com o mesmo valor, que se edita
-- se a conta do mês vier diferente.
--
-- dia_do_vencimento é a âncora do mensal, e só dele. Sem ela, vencer no dia 31 escorregaria
-- para sempre depois do primeiro mês curto: 31/01, 28/02, 28/03... Com ela, o 28/02 é só o
-- ajuste daquele mês, e março volta ao 31. O semanal não precisa: soma sete dias.
--
-- anterior_id liga a ocorrência à que a gerou, e o UNIQUE garante uma sucessora por despesa.
-- É o que impede a duplicata quando um pagamento é desfeito — a transação excluída devolve a
-- despesa ao plano (V12) — e depois refeito: a sucessora já existe, e não nasce outra.
-- SET NULL porque excluir uma ocorrência não deve apagar a vizinha.
ALTER TABLE despesas_planejadas
    ADD COLUMN recorrencia VARCHAR(7) CHECK (recorrencia IN ('SEMANAL', 'MENSAL')),
    ADD COLUMN dia_do_vencimento SMALLINT CHECK (dia_do_vencimento BETWEEN 1 AND 31),
    ADD COLUMN anterior_id BIGINT UNIQUE REFERENCES despesas_planejadas (id) ON DELETE SET NULL;

-- IS NOT DISTINCT FROM, e não "=": com recorrencia nula, "=" daria nulo, e a CHECK aceitaria
-- um dia de vencimento numa despesa que não se repete.
ALTER TABLE despesas_planejadas
    ADD CONSTRAINT ck_despesas_planejadas_dia_so_no_mensal
    CHECK ((recorrencia IS NOT DISTINCT FROM 'MENSAL') = (dia_do_vencimento IS NOT NULL));
