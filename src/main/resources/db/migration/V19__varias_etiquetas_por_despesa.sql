-- Até três etiquetas por despesa planejada, e não uma só: o cartão da compra e mais um ou dois
-- agrupamentos — "Viagem", "Casa". A coluna etiqueta_id da V17 vira uma tabela de ligação.
--
-- As etiquetas já gravadas passam para a tabela nova antes de a coluna sair: nenhuma despesa
-- perde a que tinha. O limite de três fica no domínio e na borda da API, como os limites das
-- parcelas; aqui fica o que o banco garante sozinho.

-- Alvo da chave composta da ligação, abaixo. O id já é único; o par é o que a chave exige.
ALTER TABLE despesas_planejadas
    ADD CONSTRAINT uq_despesas_planejadas_id_usuario UNIQUE (id, usuario_id);

-- As duas chaves compostas, pelo usuario_id que a ligação repete, fazem a mesma garantia da
-- V17: a despesa só se liga a etiqueta do próprio dono. Excluir a etiqueta, ou a despesa, apaga
-- só a ligação — as despesas que a etiqueta marcava ficam com as outras que tinham.
CREATE TABLE despesas_planejadas_etiquetas (
    despesa_id BIGINT NOT NULL,
    etiqueta_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT pk_despesas_planejadas_etiquetas PRIMARY KEY (despesa_id, etiqueta_id),
    CONSTRAINT fk_despesas_planejadas_etiquetas_despesa
    FOREIGN KEY (despesa_id, usuario_id) REFERENCES despesas_planejadas (id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_despesas_planejadas_etiquetas_etiqueta
    FOREIGN KEY (etiqueta_id, usuario_id) REFERENCES etiquetas (id, usuario_id) ON DELETE CASCADE
);

-- A exclusão e a contagem por etiqueta procuram pela etiqueta; a chave primária já cobre a
-- busca pela despesa.
CREATE INDEX idx_despesas_planejadas_etiquetas_etiqueta ON despesas_planejadas_etiquetas (etiqueta_id);

INSERT INTO despesas_planejadas_etiquetas (despesa_id, etiqueta_id, usuario_id)
SELECT id, etiqueta_id, usuario_id
  FROM despesas_planejadas
 WHERE etiqueta_id IS NOT NULL;

-- Leva junto a chave fk_despesas_planejadas_etiqueta e o índice idx_despesas_planejadas_etiqueta.
ALTER TABLE despesas_planejadas DROP COLUMN etiqueta_id;
