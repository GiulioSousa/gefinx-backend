-- Reduz os e-mails já cadastrados à forma canônica e impede que a coluna volte a
-- aceitar outra. O UNIQUE existente compara a string recebida, então TESTE@exemplo.com
-- e teste@exemplo.com conviviam como contas distintas da mesma pessoa.

-- Interrompe se o banco já contiver duplicatas que só diferem pela caixa: decidir qual
-- conta sobrevive é escolha de negócio, com dados de duas pessoas em jogo, e não cabe a
-- uma migration fundir ou descartar por conta própria. Melhor parar aqui do que perder
-- dados em silêncio.
DO $$
DECLARE
    duplicados TEXT;
BEGIN
    SELECT string_agg(DISTINCT lower(email), ', ')
      INTO duplicados
      FROM usuarios
     WHERE lower(trim(email)) IN (
               SELECT lower(trim(email))
                 FROM usuarios
                GROUP BY lower(trim(email))
               HAVING count(*) > 1
           );

    IF duplicados IS NOT NULL THEN
        RAISE EXCEPTION
            'Ha contas que diferem apenas pela caixa das letras: %. Consolide-as manualmente antes de aplicar esta migration.',
            duplicados;
    END IF;
END $$;

UPDATE usuarios SET email = lower(trim(email)) WHERE email <> lower(trim(email));

-- Barreira no banco para qualquer caminho futuro que esqueça de normalizar na entrada.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_email_minusculo CHECK (email = lower(email));
