-- Preparação de uma vez só do banco gefinx_db: separa quem monta o schema de quem
-- opera sobre ele.
--
-- Rode como superusuário ANTES da primeira subida da aplicação:
--   psql -h localhost -p 5433 -U postgres -d gefinx_db -f criar-roles.sql
--
-- O banco precisa existir antes. Numa instalação nova, crie-o primeiro:
--   psql -h localhost -p 5433 -U postgres -c "CREATE DATABASE gefinx_db"
--
-- Substitua os dois placeholders de senha antes de executar. Os valores reais vão para
-- application-local.yml, que o Git ignora; este arquivo é versionado e não pode conter
-- segredo, pelo mesmo motivo que application-example.yml não contém.
--
-- Não é uma migration do Flyway de propósito: criar role exige superusuário, e o Flyway
-- roda justamente *como* o usuário de migration, que neste ponto ainda não existe.
--
-- Serve aos DOIS casos, e a diferença entre eles é o que o bloco condicional resolve:
--
--   * INSTALAÇÃO NOVA — banco vazio. Não há o que transferir: as tabelas ainda não
--     existem, e o Flyway as criará já pertencendo a gefinx_migracao. Quem concede acesso
--     a gefinx_app são os privilégios padrão do fim deste arquivo, aplicados antes de a
--     primeira tabela nascer.
--   * INSTALAÇÃO EXISTENTE — o caso da Etapa 11, em que o schema fora criado pelo
--     superusuário e a posse precisava mudar de dono.
--
-- Até a Etapa 22 o script assumia o segundo caso e falhava no primeiro, em
-- `ALTER TABLE usuarios OWNER TO ...`, antes de chegar aos privilégios padrão — que são
-- justamente a parte de que uma instalação nova precisa.

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
-- Posse e acesso aos objetos QUE JÁ EXISTEM.
--
-- Objeto a objeto, deliberadamente. REASSIGN OWNED BY postgres varreria todo objeto do
-- banco pertencente ao superusuário — mais amplo do que se quer, e sem forma de conferir
-- depois o que exatamente mudou de dono.
--
-- O bloco inteiro é pulado quando não há tabela alguma, que é a situação de uma
-- instalação nova. `to_regclass` devolve NULL para relação inexistente em vez de lançar,
-- o que permite decidir sem depender de catálogo interno.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF to_regclass('public.usuarios') IS NULL THEN
        RAISE NOTICE 'Banco vazio: nada a transferir. O Flyway criará as tabelas já como gefinx_migracao, e os privilégios padrão abaixo darão acesso a gefinx_app.';
        RETURN;
    END IF;

    EXECUTE 'ALTER TABLE usuarios   OWNER TO gefinx_migracao';
    EXECUTE 'ALTER TABLE categorias OWNER TO gefinx_migracao';
    EXECUTE 'ALTER TABLE transacoes OWNER TO gefinx_migracao';

    EXECUTE 'ALTER SEQUENCE usuarios_id_seq   OWNER TO gefinx_migracao';
    EXECUTE 'ALTER SEQUENCE categorias_id_seq OWNER TO gefinx_migracao';
    EXECUTE 'ALTER SEQUENCE transacoes_id_seq OWNER TO gefinx_migracao';

    -- Runtime: só o necessário para operar. Sem DDL, sem acesso ao histórico do Flyway.
    EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON usuarios, categorias, transacoes TO gefinx_app';
    -- O INSERT depende das sequences dos BIGSERIAL.
    EXECUTE 'GRANT USAGE, SELECT ON SEQUENCE usuarios_id_seq, categorias_id_seq, transacoes_id_seq TO gefinx_app';

    -- Criada pelo Flyway na primeira subida; pode não existir ainda mesmo num banco que
    -- já tem as tabelas, se o schema veio de outra origem.
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE flyway_schema_history OWNER TO gefinx_migracao';
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Acesso ao schema. Vale para os dois casos.
-- ---------------------------------------------------------------------------

-- Permite que migrations futuras criem objetos novos no schema.
GRANT USAGE, CREATE ON SCHEMA public TO gefinx_migracao;

GRANT USAGE ON SCHEMA public TO gefinx_app;

-- ---------------------------------------------------------------------------
-- Privilégios padrão para o que as migrations criarem daqui em diante.
--
-- Sem isto, a primeira tabela nova nasceria invisível para a aplicação, e a falha só
-- apareceria no deploy seguinte — longe da migration que a causou. Numa instalação nova
-- este bloco é o que dá acesso a gefinx_app: lá, TODAS as tabelas são "criadas daqui em
-- diante", e por isso ele precisa rodar antes da primeira subida.
--
-- Provado em uso na Etapa 19: a tabela `contas`, criada pela V7 muito depois deste
-- script, nasceu acessível a gefinx_app sem grant manual nenhum.
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
