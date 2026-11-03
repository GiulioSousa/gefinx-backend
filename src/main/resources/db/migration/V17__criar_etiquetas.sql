-- Etiquetas das despesas planejadas: um nome e uma cor, para marcar de que cartão é a compra
-- — "Nubank", "Inter" — e ver isso de relance no plano. Uma por despesa, opcional.
--
-- Nada aqui mexe em dado já gravado: a coluna nova nasce nula.

-- A cor é uma das dez da paleta, e não um #rrggbb: cada uma tem um par medido para o tema
-- claro e outro para o escuro, e uma cor livre não teria contraste garantido nos dois.
CREATE TABLE etiquetas (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    nome VARCHAR(30) NOT NULL,
    cor VARCHAR(10) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_etiquetas_cor
    CHECK (cor IN ('ROXO', 'INDIGO', 'AZUL', 'CIANO', 'VERDE', 'AMBAR', 'LARANJA', 'VERMELHO', 'ROSA', 'CINZA')),
    -- O nome chega aparado da borda: espaço sobrando faria "Nubank " e "Nubank" parecerem
    -- duas etiquetas iguais que o índice abaixo não vê como iguais.
    CONSTRAINT ck_etiquetas_nome_aparado CHECK (nome = btrim(nome) AND nome <> ''),
    -- Alvo da chave estrangeira composta das despesas, abaixo.
    CONSTRAINT uq_etiquetas_id_usuario UNIQUE (id, usuario_id)
);

-- "Nubank" e "nubank" são a mesma etiqueta.
CREATE UNIQUE INDEX uq_etiquetas_usuario_nome ON etiquetas (usuario_id, lower(nome));

-- A chave é composta para que a despesa só aponte para uma etiqueta do mesmo dono: o
-- serviço confere, e o banco torna o contrário impossível. Excluir a etiqueta limpa só
-- etiqueta_id — sem a lista de colunas, o SET NULL limparia também o usuario_id, que é NOT
-- NULL, e a exclusão falharia.
ALTER TABLE despesas_planejadas
    ADD COLUMN etiqueta_id BIGINT,
    ADD CONSTRAINT fk_despesas_planejadas_etiqueta
    FOREIGN KEY (etiqueta_id, usuario_id) REFERENCES etiquetas (id, usuario_id)
    ON DELETE SET NULL (etiqueta_id);

-- A exclusão da etiqueta procura as despesas que apontam para ela.
CREATE INDEX idx_despesas_planejadas_etiqueta ON despesas_planejadas (etiqueta_id);
