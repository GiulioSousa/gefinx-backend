-- Custo diário do planejamento: as categorias de despesa cujo gasto médio dos últimos 30 dias
-- entra na meta diária, como alimentação e combustível. Quem escolhe é a tela, pelo id: o
-- nome da categoria é livre, e comparar por ele quebraria ao renomear — o mesmo motivo que
-- levou as contas de fora a serem guardadas por id na V11.
--
-- Nada aqui mexe em dado já gravado. A V18 estava reservada ao orçamento mensal, que ainda
-- está na fila e passa para a próxima.

-- Alvo da chave composta abaixo. As categorias já tinham (id, tipo) referenciável, da V5,
-- mas não (id, usuario_id).
ALTER TABLE categorias ADD CONSTRAINT uq_categorias_id_usuario UNIQUE (id, usuario_id);

-- A chave composta amarra a linha ao dono da categoria: com referência simples a
-- `categorias(id)`, o banco aceitaria marcar a categoria de outra pessoa. O CASCADE faz a
-- exclusão da categoria levar a marca junto, em vez de ser barrada por ela.
--
-- O tipo não é amarrado aqui: o serviço só aceita categoria de despesa, e uma que deixasse
-- de ser simplesmente não teria gasto a somar.
CREATE TABLE categorias_do_custo_diario (
    categoria_id BIGINT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    CONSTRAINT fk_categoria_do_custo_diario FOREIGN KEY (categoria_id, usuario_id)
        REFERENCES categorias (id, usuario_id) ON DELETE CASCADE
);

CREATE INDEX idx_categorias_do_custo_diario_usuario ON categorias_do_custo_diario (usuario_id);
