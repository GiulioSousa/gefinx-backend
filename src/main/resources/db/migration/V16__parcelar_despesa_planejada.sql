-- Despesa planejada parcelada: a compra no cartão em 6 vezes, a mensalidade de um curso de 12
-- meses. É uma recorrente com fim.
--
-- Cada ocorrência leva o seu número — parcela de total_de_parcelas —, e a seguinte nasce com o
-- próximo; a última não gera outra. Sem os dois, a recorrente segue sem fim, como antes.
--
-- Nada aqui mexe em dado já gravado: as colunas nascem nulas.
ALTER TABLE despesas_planejadas
    ADD COLUMN parcela SMALLINT,
    ADD COLUMN total_de_parcelas SMALLINT;

-- Os dois juntos ou nenhum, a parcela dentro do total, e no máximo 999 — o mesmo limite da
-- borda, folgado para um financiamento e curto para um erro de digitação.
ALTER TABLE despesas_planejadas
    ADD CONSTRAINT ck_despesas_planejadas_parcelas_juntas
    CHECK ((parcela IS NULL) = (total_de_parcelas IS NULL)),
    ADD CONSTRAINT ck_despesas_planejadas_parcela_no_total
    CHECK (parcela BETWEEN 1 AND total_de_parcelas AND total_de_parcelas <= 999);

-- Só a que se repete tem parcelas: a que não se repete é uma só, e "1 de 1" não diria nada.
ALTER TABLE despesas_planejadas
    ADD CONSTRAINT ck_despesas_planejadas_parcelas_so_na_recorrente
    CHECK (parcela IS NULL OR recorrencia IS NOT NULL);
