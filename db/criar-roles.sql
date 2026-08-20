-- Preparação de uma vez só do banco gefinx_db: separa quem monta o schema de quem
-- opera sobre ele.
--
-- Rode como superusuário ANTES da primeira subida da aplicação:
--   psql -h localhost -p 5433 -U postgres -d gefinx_db -f criar-roles.sql
--
-- Substitua os dois placeholders de senha antes de executar. Os valores reais vão para
-- application-local.yml, que o Git ignora; este arquivo é versionado e não pode conter
-- segredo, pelo mesmo motivo que application-example.yml não contém.
--
-- Não é uma migration do Flyway de propósito: criar role exige superusuário, e o Flyway
-- roda justamente *como* o usuário de migration, que neste ponto ainda não existe.

-- ---------------------------------------------------------------------------
-- As duas roles. Guardadas por IF NOT EXISTS para o script poder ser reexecutado.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gefinx_migracao') THEN
        CREATE ROLE gefinx_migracao LOGIN PASSWORD 'SENHA_DA_MIGRACAO';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gefinx_app') THEN
        CREATE ROLE gefinx_app LOGIN PASSWORD 'SENHA_DA_APLICACAO';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Posse dos objetos existentes passa para a role de migration.
--
-- Um a um, deliberadamente. REASSIGN OWNED BY postgres varreria todo objeto do banco
-- pertencente ao superusuário — mais amplo do que se quer, e sem forma de conferir
-- depois o que exatamente mudou de dono.
-- ---------------------------------------------------------------------------
ALTER TABLE usuarios              OWNER TO gefinx_migracao;
ALTER TABLE categorias            OWNER TO gefinx_migracao;
ALTER TABLE transacoes            OWNER TO gefinx_migracao;
ALTER TABLE flyway_schema_history OWNER TO gefinx_migracao;

ALTER SEQUENCE usuarios_id_seq   OWNER TO gefinx_migracao;
ALTER SEQUENCE categorias_id_seq OWNER TO gefinx_migracao;
ALTER SEQUENCE transacoes_id_seq OWNER TO gefinx_migracao;

-- Permite que migrations futuras criem objetos novos no schema.
GRANT USAGE, CREATE ON SCHEMA public TO gefinx_migracao;

-- ---------------------------------------------------------------------------
-- Runtime: só o necessário para operar. Sem DDL, sem acesso ao histórico do Flyway.
-- ---------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO gefinx_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios, categorias, transacoes TO gefinx_app;

-- O INSERT depende das sequences dos BIGSERIAL.
GRANT USAGE, SELECT ON SEQUENCE usuarios_id_seq, categorias_id_seq, transacoes_id_seq TO gefinx_app;

-- ---------------------------------------------------------------------------
-- Privilégios padrão para o que as migrations criarem daqui em diante.
--
-- Sem isto, a primeira tabela nova nasceria invisível para a aplicação, e a falha só
-- apareceria no deploy seguinte — longe da migration que a causou.
-- ---------------------------------------------------------------------------
ALTER DEFAULT PRIVILEGES FOR ROLE gefinx_migracao IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO gefinx_app;

ALTER DEFAULT PRIVILEGES FOR ROLE gefinx_migracao IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO gefinx_app;

-- ---------------------------------------------------------------------------
-- Quem pode sequer conectar neste banco.
--
-- Por padrão o PostgreSQL concede CONNECT a PUBLIC, isto é, a qualquer role da
-- instância. Restringir fecha gefinx_db para quem não é deste projeto.
--
-- O caminho inverso — impedir que gefinx_app conecte em OUTROS bancos — não se
-- resolve aqui: depende de revogar CONNECT de PUBLIC naqueles bancos, decisão que é da
-- instância inteira e não deste projeto. Ele continua podendo abrir conexão em outro
-- banco e ler o catálogo, ainda que sem enxergar dado de aplicação alguma sem grant.
-- ---------------------------------------------------------------------------
REVOKE CONNECT ON DATABASE gefinx_db FROM PUBLIC;
GRANT CONNECT ON DATABASE gefinx_db TO gefinx_migracao, gefinx_app;
