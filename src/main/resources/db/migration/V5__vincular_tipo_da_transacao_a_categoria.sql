-- Amarra o tipo da transação ao tipo da sua categoria.
--
-- Uma CHECK não alcança este caso, porque a regra cruza duas tabelas. Uma chave
-- estrangeira composta alcança: se (categoria_id, tipo) precisa existir em
-- (categorias.id, categorias.tipo), então nem uma transação nasce divergente, nem o tipo
-- de uma categoria pode mudar enquanto houver transações apontando para o par antigo.
-- Uma restrição resolve os dois lados.

-- Interrompe se o banco já contiver divergências: consertá-las significa decidir se a
-- transação estava errada ou a categoria, o que muda o saldo de alguém. Não é escolha
-- de migration.
DO $$
DECLARE
    divergentes TEXT;
BEGIN
    SELECT string_agg(t.id::text, ', ' ORDER BY t.id)
      INTO divergentes
      FROM transacoes t
      JOIN categorias c ON c.id = t.categoria_id
     WHERE t.tipo <> c.tipo;

    IF divergentes IS NOT NULL THEN
        RAISE EXCEPTION
            'Ha transacoes cujo tipo diverge do tipo da categoria (ids: %). Corrija-as antes de aplicar esta migration.',
            divergentes;
    END IF;
END $$;

-- Alvo que a chave composta precisa referenciar. O id sozinho já é único pela PK; este
-- UNIQUE existe para tornar o par referenciável.
ALTER TABLE categorias ADD CONSTRAINT uk_categoria_id_tipo UNIQUE (id, tipo);

-- A composta subsume a antiga: casar (categoria_id, tipo) implica que categoria_id existe.
ALTER TABLE transacoes DROP CONSTRAINT transacoes_categoria_id_fkey;
ALTER TABLE transacoes ADD CONSTRAINT fk_transacao_categoria_do_mesmo_tipo
    FOREIGN KEY (categoria_id, tipo) REFERENCES categorias (id, tipo);
