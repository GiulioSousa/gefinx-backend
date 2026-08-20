CREATE TABLE categorias (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(80) NOT NULL,
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('RECEITA', 'DESPESA')),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_categoria_nome_tipo_usuario UNIQUE (nome, tipo, usuario_id)
);

CREATE INDEX idx_categorias_usuario ON categorias(usuario_id);
