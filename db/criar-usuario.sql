-- Cria uma conta de acesso ao GeFinX, com as categorias e a conta padrão que o cadastro
-- pela interface criava antes de ser removido.
--
-- COMO RODAR
--
--   psql -h localhost -p 5433 -U postgres -d gefinx_db \
--        -v usuario=giulivan -v senha='uma frase de senha longa' -f criar-usuario.sql
--
-- A senha vai na linha de comando e fica no histórico do shell. Para evitar isso, abra o
-- psql e informe-a na hora:
--
--   psql -h localhost -p 5433 -U postgres -d gefinx_db
--   \set usuario giulivan
--   \prompt 'Senha: ' senha
--   \i criar-usuario.sql
--
-- O `\prompt` mostra o que é digitado na tela — ele tira a senha do histórico, não dos
-- olhos de quem estiver por perto.
--
-- POR QUE ISTO EXISTE
--
-- Não há mais rota de cadastro. Sem verificação de e-mail, aquela rota deixava qualquer
-- um que alcançasse o endereço criar conta e usar o sistema; e verificar endereço exigiria
-- um canal de envio que o projeto não tem. A conta passa a nascer aqui, por quem opera o
-- banco — o que também explica a semente: o RegistroUseCase criava as categorias padrão e
-- a conta padrão junto com o usuário, e sem elas a primeira tela útil viria vazia e nenhum
-- lançamento seria possível, porque toda transação exige uma conta.
--
-- Não é uma migration do Flyway: migration descreve o schema, e isto é dado — rodaria uma
-- vez por instalação, com valores diferentes em cada uma.
--
-- SOBRE O HASH
--
-- O pgcrypto gera BCrypt no mesmo formato que o BCryptPasswordEncoder do Spring lê
-- (`$2a$10$...`), então a senha criada aqui vale no login sem nenhum passo intermediário.
-- Criar a extensão exige superusuário, e é por isso que o script é rodado como `postgres`
-- e não como `gefinx_app`.
--
-- O custo do `gen_salt('bf', N)` lá embaixo precisa acompanhar o do BCryptPasswordEncoder, no
-- SecurityConfig. Divergir não impede ninguém de entrar — o `matches` lê o custo de dentro do
-- hash —, mas derruba a defesa contra enumeração: aquele bean governa o hash descartável que o
-- AutenticacaoService confere quando a conta não existe, e um custo menor ali torna a resposta
-- da conta inexistente mais rápida que a da conta real. O motivo completo está no SecurityConfig
-- e no item 13.2 do GUIA-DEPLOY.md.
--
-- A senha em texto puro passa pela conexão e pode aparecer no log do servidor, se ele
-- estiver com `log_statement = all`. Num banco pessoal isso é aceitável; num banco
-- compartilhado, confira antes.

\set ON_ERROR_STOP on

-- O arquivo é UTF-8 e tem acentos nas categorias padrão. No Windows o cliente costuma
-- assumir WIN1252, e sem esta linha "Salário" entraria corrompido na tabela.
\encoding UTF8

-- ---------------------------------------------------------------------------
-- Conferências antes de escrever. Todas param o script com uma mensagem em vez de
-- deixarem o erro estourar do meio do INSERT.
-- ---------------------------------------------------------------------------

\if :{?usuario}
\else
  \echo 'ERRO: defina o nome de usuario. Ex.: -v usuario=giulivan'
  \quit
\endif

\if :{?senha}
\else
  \echo 'ERRO: defina a senha. Ex.: -v senha=... ou \\prompt ''Senha: '' senha'
  \quit
\endif

-- A forma canônica é a que o login procura: minúscula e sem espaços nas pontas. O script
-- normaliza em vez de recusar, para que `-v usuario=Giulivan` não vire uma conta que não
-- abre. A mesma regra vive no NormalizadorDeUsuario e numa CHECK da tabela.
SELECT lower(btrim(:'usuario')) AS usuario_canonico \gset

SELECT length(:'usuario_canonico') BETWEEN 3 AND 60 AS nome_cabe \gset
\if :nome_cabe
\else
  \echo 'ERRO: o nome de usuario precisa ter de 3 a 60 caracteres.'
  \quit
\endif

SELECT NOT EXISTS (SELECT 1 FROM usuarios WHERE usuario = :'usuario_canonico') AS nome_livre \gset
\if :nome_livre
\else
  \echo 'ERRO: ja existe uma conta com esse nome de usuario.'
  \quit
\endif

-- Dez caracteres era o piso que a política de senha exigia no cadastro, e continua sendo
-- o piso aqui: a rota saiu, a razão não. O teto é do próprio BCrypt, que não olha além do
-- 72º byte — e o limite é em BYTES, o que pesa em português, onde cada acento ocupa dois.
-- Sem esta conferência, o excedente seria truncado em silêncio e a senha efetiva seria
-- mais curta do que a escolhida.
SELECT length(:'senha') >= 10 AND octet_length(:'senha') <= 72 AS senha_ok \gset
\if :senha_ok
\else
  \echo 'ERRO: a senha precisa ter no minimo 10 caracteres e no maximo 72 bytes (acentos contam dois).'
  \quit
\endif

-- ---------------------------------------------------------------------------
-- A escrita. Uma transação só: uma conta sem as categorias e a conta padrão seria pior do
-- que conta nenhuma, porque pareceria pronta.
-- ---------------------------------------------------------------------------

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- `sessoes_validas_apos` nasce igual a `criado_em`: nenhum token foi emitido antes disso,
-- então não há sessão anterior para invalidar.
--
-- Os três INSERTs vão numa instrução só, encadeados por CTE, para que o id recém-criado
-- chegue aos outros dois sem depender de currval nem de uma segunda consulta. CTE que
-- escreve roda sempre, mesmo que a consulta principal não leia o resultado dela.
WITH nova_conta_de_acesso AS (
    INSERT INTO usuarios (usuario, senha_hash, criado_em, sessoes_validas_apos)
    VALUES (:'usuario_canonico', crypt(:'senha', gen_salt('bf', 10)), now(), now())
    RETURNING id
),
categorias_padrao AS (
    -- Mesma lista que o CategoriaService usava no cadastro. Se ela mudar lá, muda aqui.
    INSERT INTO categorias (nome, tipo, usuario_id)
    SELECT padrao.nome, padrao.tipo, nova_conta_de_acesso.id
      FROM nova_conta_de_acesso,
           (VALUES
               ('Salário',      'RECEITA'),
               ('Outras Receitas', 'RECEITA'),
               ('Alimentação',  'DESPESA'),
               ('Transporte',   'DESPESA'),
               ('Moradia',      'DESPESA'),
               ('Lazer',        'DESPESA'),
               ('Saúde',        'DESPESA'),
               ('Outros',       'DESPESA')
           ) AS padrao(nome, tipo)
    RETURNING 1
)
-- Mesmo nome que a migration V7 deu às contas do backfill e que o ContaService usava no
-- cadastro: quem entra pela primeira vez precisa encontrar a mesma coisa por qualquer um
-- dos três caminhos.
INSERT INTO contas (nome, usuario_id)
SELECT 'Conta principal', id FROM nova_conta_de_acesso;

COMMIT;

\echo 'Conta criada. Entre no GeFinX com o nome de usuario e a senha informados.'
