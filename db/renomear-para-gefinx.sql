-- Renomeia banco e roles de financas_* para gefinx_*, acompanhando o nome do sistema.
--
-- Rode como superusuário, conectado a OUTRO banco e sem nenhuma sessão aberta no antigo:
--   psql -h localhost -p 5433 -U postgres -d postgres -f renomear-para-gefinx.sql
--
-- Conectado a `postgres`, e não a `financas_db`, porque não se renomeia o banco em que se
-- está. E sem outras sessões porque ALTER DATABASE ... RENAME recusa enquanto houver
-- qualquer conexão aberta — pare a aplicação antes.
--
-- É passo manual pelo mesmo motivo que criar-roles.sql: renomear banco ou role exige
-- superusuário, e nem financas_app nem financas_migracao têm CREATEDB ou CREATEROLE.
-- Instalação nova não precisa deste arquivo — criar-roles.sql já nasce com o nome novo.
--
-- As senhas sobrevivem ao rename: com `password_encryption = scram-sha-256`, o hash não
-- usa o nome da role como sal. Sob md5 elas seriam apagadas, e teriam de ser redefinidas
-- em seguida. Confira antes com:  SHOW password_encryption;
--
-- Posse, grants e privilégios padrão também sobrevivem, por serem guardados por OID e não
-- por nome. application-local.yml não precisa mudar: lá só há senha.

-- ---------------------------------------------------------------------------
-- Cada comando é gerado condicionalmente e executado por \gexec, para o script poder ser
-- reexecutado sem erro. A forma aqui é uniforme, e não o bloco DO usado em criar-roles.sql,
-- porque ALTER DATABASE ... RENAME não roda dentro de transação — e todo DO é uma.
-- ---------------------------------------------------------------------------
SELECT 'ALTER ROLE financas_migracao RENAME TO gefinx_migracao'
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'financas_migracao')
  AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gefinx_migracao');
\gexec

SELECT 'ALTER ROLE financas_app RENAME TO gefinx_app'
WHERE EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'financas_app')
  AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gefinx_app');
\gexec

SELECT 'ALTER DATABASE financas_db RENAME TO gefinx_db'
WHERE EXISTS (SELECT 1 FROM pg_database WHERE datname = 'financas_db')
  AND NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'gefinx_db');
\gexec

-- ---------------------------------------------------------------------------
-- Conferência: devem sair gefinx_app, gefinx_migracao e gefinx_db.
-- ---------------------------------------------------------------------------
SELECT rolname AS role FROM pg_roles WHERE rolname IN ('gefinx_app', 'gefinx_migracao') ORDER BY rolname;
SELECT datname AS banco FROM pg_database WHERE datname = 'gefinx_db';
