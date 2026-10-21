-- Calendário de trabalho: os feriados nacionais que se decidiu ignorar, e os períodos sem
-- trabalho de cada um.
--
-- Os feriados nacionais não estão aqui: são calculados no código, para qualquer ano, e não há
-- lista a manter. Guarda-se só a exceção — o feriado em que a pessoa decidiu trabalhar.
--
-- Nada aqui mexe em dado já gravado.

CREATE TABLE feriados_ignorados (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    data DATE NOT NULL,
    -- Ignorar duas vezes é ignorar uma: o INSERT usa ON CONFLICT contra esta chave, e dois
    -- cliques seguidos não viram erro.
    CONSTRAINT uq_feriados_ignorados_usuario_data UNIQUE (usuario_id, data)
);

-- Feriado da cidade, folga, férias: um dia só é o período de início e fim iguais. A
-- descrição é opcional. O teto de 366 dias é o mesmo do código, para que um ano digitado
-- errado não vire um período de séculos.
CREATE TABLE periodos_sem_trabalho (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    inicio DATE NOT NULL,
    fim DATE NOT NULL,
    descricao VARCHAR(100),
    criado_em TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_periodos_sem_trabalho_fim_depois_do_inicio CHECK (fim >= inicio),
    CONSTRAINT ck_periodos_sem_trabalho_duracao CHECK (fim - inicio < 366)
);

-- A leitura é sempre "os do usuário, por início".
CREATE INDEX idx_periodos_sem_trabalho_usuario_inicio ON periodos_sem_trabalho (usuario_id, inicio);
