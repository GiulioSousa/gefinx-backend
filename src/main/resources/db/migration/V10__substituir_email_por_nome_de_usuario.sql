-- Troca o e-mail pelo nome de usuário como identificador de login, e descarta o nome de
-- exibição.
--
-- O sistema deixou de ter cadastro pela interface: quem opera cria a conta direto no
-- banco, com backend/db/criar-usuario.sql. Isso muda o que o e-mail era aqui. Ele nunca
-- foi verificado — qualquer endereço inventado abria conta — e o sistema nunca enviou
-- mensagem alguma, nem tem recuperação de senha. Guardá-lo dava a impressão de um canal
-- de contato que não existe, e cobrava por isso o preço de um dado pessoal a mais na
-- tabela. O nome de exibição cai junto: com uma conta criada à mão, ele seria uma
-- terceira coluna para preencher só para escrever no cabeçalho o que o nome de usuário
-- já diz.
--
-- O identificador continua canônico — minúsculo e sem espaços nas pontas —, pelo mesmo
-- motivo da V4: a unicidade compara a string gravada, e é a entrada normalizada que
-- impede duas contas para a mesma pessoa. A CHECK do fim vale ainda mais aqui do que lá,
-- porque agora a linha é inserida por um humano num terminal, sem a borda da aplicação
-- entre ele e a tabela: um `'fulano '` com espaço ao final entraria, e o login, que
-- procura pela forma canônica, nunca mais acharia essa conta.

-- Interrompe antes de tocar em qualquer linha se a derivação não servir. Escolher o
-- nome de usuário de alguém não é decisão de migration, e um nome errado aqui é uma
-- conta que não abre mais.
DO $$
DECLARE
    duplicados TEXT;
    curtos_ou_longos TEXT;
BEGIN
    SELECT string_agg(DISTINCT derivado, ', ')
      INTO duplicados
      FROM (SELECT lower(btrim(split_part(email, '@', 1))) AS derivado FROM usuarios) d
     WHERE d.derivado IN (
               SELECT lower(btrim(split_part(email, '@', 1)))
                 FROM usuarios
                GROUP BY 1
               HAVING count(*) > 1
           );

    IF duplicados IS NOT NULL THEN
        RAISE EXCEPTION
            'A parte antes do @ colide entre contas diferentes: %. Renomeie o e-mail de uma delas (UPDATE usuarios SET email = ... WHERE id = ...) e rode a migration de novo.',
            duplicados;
    END IF;

    SELECT string_agg(email, ', ' ORDER BY email)
      INTO curtos_ou_longos
      FROM usuarios
     WHERE length(lower(btrim(split_part(email, '@', 1)))) NOT BETWEEN 3 AND 60;

    IF curtos_ou_longos IS NOT NULL THEN
        RAISE EXCEPTION
            'A parte antes do @ nao cabe em um nome de usuario (3 a 60 caracteres): %. Ajuste o e-mail dessas contas antes de rodar a migration.',
            curtos_ou_longos;
    END IF;
END $$;

-- A CHECK da V4 fala de e-mail e some junto com ele; a canônica entra no fim, depois de
-- os valores já estarem na forma nova.
ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_email_minusculo;

ALTER TABLE usuarios RENAME COLUMN email TO usuario;

-- O UNIQUE veio sem nome próprio da V1, como usuarios_email_key. Renomeado junto com a
-- coluna: um índice chamado "email" numa tabela sem coluna de e-mail é uma pista falsa
-- deixada para quem for ler o schema daqui a um ano.
ALTER TABLE usuarios RENAME CONSTRAINT usuarios_email_key TO uk_usuarios_usuario;

-- A parte antes do @ é o ponto de partida, não uma escolha definitiva: quem opera pode
-- trocar depois com um UPDATE, já que nada além do login depende deste valor.
UPDATE usuarios SET usuario = lower(btrim(split_part(usuario, '@', 1)));

-- Só depois do UPDATE: os 180 caracteres do e-mail não cabem em 60, e apertar a coluna
-- antes recusaria endereços que a derivação encurtaria em seguida.
ALTER TABLE usuarios ALTER COLUMN usuario TYPE VARCHAR(60);

ALTER TABLE usuarios DROP COLUMN nome;

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_usuario_canonico
    CHECK (usuario = lower(btrim(usuario)) AND length(usuario) BETWEEN 3 AND 60);
