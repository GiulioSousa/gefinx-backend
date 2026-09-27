# GeFinX — Backend

API REST do **GeFinX**, um gerenciador financeiro pessoal: o usuário cadastra receitas e despesas
classificadas por categoria e acompanha o saldo. Multiusuário — cada conta enxerga apenas os
próprios dados.

Spring Boot 4 com arquitetura orientada a domínio (DDD), organizada por *bounded context*. A
interface web vive em repositório próprio,
[gefinx-frontend](https://github.com/GiulioSousa/gefinx-frontend), e essa separação é deliberada:
o objetivo de médio prazo é extrair alguns contextos como serviços independentes.

> Projeto pessoal, de estudo, sem uso comercial.

---

## Requisitos

| | Versão usada |
|---|---|
| Java | 25 (JDK 25.0.3) |
| Maven | 3.9.9 |
| PostgreSQL | 18.3, escutando na porta **5433** |

Sem containers — decisão explícita. O PostgreSQL roda como serviço local.

---

## Configuração

### 1. Crie o banco

```bash
psql -U postgres -p 5433 -c "CREATE DATABASE gefinx_db WITH ENCODING 'UTF8';"
```

### 2. Crie as roles da aplicação

**Este passo não é opcional.** A aplicação não conecta como superusuário: usa duas credenciais
separadas, e sem elas não sobe.

```bash
psql -h localhost -p 5433 -U postgres -d gefinx_db -f db/criar-roles.sql
```

Antes de rodar, substitua no script os dois marcadores de senha. Ele cria:

- **`gefinx_migracao`** — dona do schema, com DDL. Usada apenas pelo Flyway, durante a subida.
- **`gefinx_app`** — execução, restrita a `SELECT`/`INSERT`/`UPDATE`/`DELETE` nas três tabelas.

Separá-las é o ponto: comprometer a aplicação em execução não dá poder de alterar nem destruir o
schema. O script também revoga `CONNECT` de `PUBLIC` no banco.

Não é uma migration do Flyway de propósito — criar role exige superusuário, e o Flyway roda
justamente *como* a role de migração, que nesse momento ainda não existe.

### 3. Crie a conta de acesso

Não há cadastro pela interface: sem verificação de e-mail, a rota aberta deixava qualquer um que
alcançasse o endereço criar conta e usar o sistema. A conta nasce no banco, **depois da primeira
subida** (o Flyway precisa ter criado as tabelas):

```bash
psql -h localhost -p 5433 -U postgres -d gefinx_db -v usuario=fulano -v senha='uma frase de senha longa' -f db/criar-usuario.sql
```

O script cria o usuário com hash BCrypt via `pgcrypto` — no mesmo formato que o
`BCryptPasswordEncoder` lê — e semeia as categorias padrão e a conta padrão, que antes vinham
pelo cadastro. Como superusuário porque `CREATE EXTENSION pgcrypto` exige esse privilégio.

A senha na linha de comando fica no histórico do shell; o cabeçalho do script mostra a alternativa
interativa, com `\prompt`.

### 4. Preencha a configuração local

```bash
cp src/main/resources/application-example.yml src/main/resources/application-local.yml
```

`application-local.yml` é ignorado pelo Git e guarda as duas senhas do banco e a chave JWT.
Gere uma chave nova (Base64, mínimo 48 bytes para HS384) com:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

### 5. Suba

```bash
mvn spring-boot:run
```

O Flyway aplica as migrations `V1`–`V9` automaticamente. A API fica em
`http://localhost:8080/api`.

### Em produção

A aplicação não guarda segredo em arquivo versionado: `DB_PASSWORD` e `JWT_SECRET` não têm valor
padrão, e a subida **falha** se faltarem — que é o comportamento desejado.

#### Variáveis

| Variável | Obrigatória | Para quê |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | Conexão de execução (`gefinx_app`) |
| `DB_MIGRACAO_USERNAME`, `DB_MIGRACAO_PASSWORD` | sim | Credencial do Flyway (`gefinx_migracao`) |
| `JWT_SECRET` | sim | Base64, mínimo 48 bytes para HS384 |
| `JWT_EXPIRACAO_MINUTOS` | não | Padrão 1440 (24h) |
| `SERVER_ADDRESS` | **sim, atrás de proxy** | `127.0.0.1` — o proxy passa a ser a única entrada |
| `FORWARD_HEADERS_STRATEGY` | **sim, atrás de proxy** | `FRAMEWORK` — ver o aviso abaixo |
| `CORS_ORIGENS` | não | Vazio quando frontend e API dividem o domínio |
| `MANAGEMENT_PORT` | não | Padrão 8081, sempre em `127.0.0.1` |

#### O par que precisa andar junto

`FORWARD_HEADERS_STRATEGY=FRAMEWORK` e `SERVER_ADDRESS=127.0.0.1` **só são seguros juntos**, e
definir um sem o outro é pior do que não definir nenhum:

- **Sem `FRAMEWORK`**, atrás de um proxy, `getRemoteAddr()` devolve o endereço do próprio proxy em
  toda requisição. Os baldes do limite por origem colapsam num só: o primeiro usuário que errar
  cinco senhas tranca o login de todos. A proteção vira negação de serviço.
- **Com `FRAMEWORK` mas sem o bind em loopback**, quem alcançar a aplicação diretamente forja o
  próprio `X-Forwarded-For` e escapa do limite à vontade.

O nginx precisa **sobrescrever** o cabeçalho, e não repassar o que o cliente mandou:

```nginx
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Host $host;
```

#### Roteiro — VPS com nginx e systemd

1. **Banco.** `CREATE DATABASE gefinx_db`, depois `db/criar-roles.sql` como superusuário, com as
   senhas reais no lugar dos marcadores. O script funciona em banco vazio. A conta de acesso vem
   depois, com `db/criar-usuario.sql`, quando o Flyway já tiver criado as tabelas na primeira
   subida do backend.
2. **Backend.** `mvn clean package` gera o jar; rode-o por uma unit do systemd com
   `Restart=always`, usuário sem privilégio, e **`WorkingDirectory` explícito** — `logging.file.name`
   é relativo, e sem isso a trilha de auditoria vai parar em lugar imprevisível. Os segredos vão num
   `EnvironmentFile` com permissão `600`, fora do repositório.
3. **Frontend.** `npm ci && npm run build` **no servidor**, e sirva o `dist/` pelo nginx.

   > O Vite embute `.env.local` também no build de produção. Buildar na máquina de desenvolvimento
   > e enviar o `dist/` publica um frontend que fala com `http://localhost:8080` — o localhost de
   > quem visita. Se precisar buildar fora do servidor, remova o `.env.local` antes e confira com
   > `grep -r "localhost:8080" dist/`.

4. **nginx.** Servir o `dist/` e fazer `proxy_pass` de `/api` para `127.0.0.1:8080`, no **mesmo
   domínio**: assim frontend e API ficam na mesma origem, e o CORS deixa de ser exercido. As rotas
   do React Router precisam de `try_files $uri $uri/ /index.html`, senão recarregar `/transacoes`
   devolve 404.
5. **TLS.** Let's Encrypt, com 80 redirecionando para 443. Sem isso o JWT viaja em claro, e
   interceptar a rede é capturar a sessão.
6. **Firewall.** Só 22, 80 e 443. A porta da API (8080), a de monitoramento (8081) e o PostgreSQL
   nunca devem ser alcançáveis de fora.
7. **Backup.** `pg_dump` periódico, com retenção definida — e **restauração testada pelo menos uma
   vez**. Backup nunca restaurado não é backup, e aqui o dado é financeiro.

#### Health check

`GET /actuator/health` na porta de monitoramento, ligada a `127.0.0.1` e fora do proxy:

```bash
curl 127.0.0.1:8081/actuator/health   # {"status":"UP"}
```

Só `health` está exposto; qualquer outro endpoint do Actuator responde `401`. O corpo não traz
detalhe de banco, disco ou versão.

#### Antes da primeira subida

As migrations `V1`–`V9` sempre foram aplicadas sobre um banco que já existia. **Aplicá-las num
banco vazio ainda não foi exercitado** — a análise indica que são inofensivas sem dados (as guardas
da `V4` e da `V7` contam zero e não disparam), mas isso é análise, não prova. Faça o ensaio num
banco descartável antes de valer para produção, e confirme:

```sql
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
```

Nove linhas, todas com `success = t`.

---

## Testes

```bash
mvn test
```

**89 testes.** A maioria roda sem contexto Spring nem banco, mas cinco classes sobem a aplicação
inteira e **exigem PostgreSQL no ar**: `BackendApplicationTests`, `LimiteDeRequisicoesTest`,
`RevogacaoDeSessaoTest`, `TratamentoDeErrosTest` e `CorsTest`. Elas criam contas de verdade e as
apagam num `@AfterEach`.

Sobem Tomcat real de propósito. O que testam — desvio de rota por *percent-encoding*, corpo que o
conversor não consegue ler, cabeçalho de CORS que chega ao navegador — nasce **antes** do
controlador, e um teste em memória escolheria uma das duas visões do caminho e nunca veria a
divergência.

---

## Arquitetura

Organização por **bounded context**, cada um em quatro camadas:

```
com.gefinx.backend
├── usuarios/        # identidade, autenticação e sessões
├── financas/        # categorias, transações e saldo
└── compartilhado/   # segurança, CORS, erros e trilha de auditoria
```

| Camada | Responsabilidade |
|---|---|
| `dominio` | Entidades e portas de repositório, **sem** dependência de Spring ou JPA |
| `aplicacao` | Casos de uso, orquestrando as portas do domínio |
| `infraestrutura` | Adaptadores JPA e serviços técnicos (JWT, cache de sessões) |
| `interfaces/web` | Controllers REST e DTOs |

O acoplamento entre contextos é hoje **nulo**: `usuarios` e `financas` não se referenciam. A única
travessia que existia era o `RegistroUseCase`, que criava o usuário e suas categorias padrão junto;
com o cadastro removido, ela virou o `db/criar-usuario.sql` — e o ponto que precisaria virar
comunicação entre serviços numa extração futura deixou de existir no código.

### Modelo de dados

| Tabela | Observações |
|---|---|
| `usuarios` | Login por `usuario` (3–60, único); senha em BCrypt; `CHECK (usuario = lower(btrim(usuario)))`; `sessoes_validas_apos` |
| `categorias` | Único por `(nome, tipo, usuario_id)` |
| `transacoes` | `valor NUMERIC(14,2) > 0`; o sinal vem de `tipo` |

O tipo da transação é amarrado ao da sua categoria por **chave estrangeira composta** sobre
`(categoria_id, tipo)` — uma `CHECK` não alcançaria, por cruzar duas tabelas. Sem isso, a soma por
categoria e a soma por tipo podiam divergir sem explicação.

Colunas, classes e métodos em **português**.

---

## Endpoints

Base: `http://localhost:8080/api`

| Método | Rota | Auth | Retorno |
|---|---|---|---|
| `POST` | `/auth/login` | pública | `200` + `{token, usuario}` |
| `GET` `POST` | `/categorias` | Bearer | lista / `201` |
| `PUT` `DELETE` | `/categorias/{id}` | Bearer | `200` / `204` |
| `GET` `POST` | `/transacoes` | Bearer | lista / `201` |
| `PUT` `DELETE` | `/transacoes/{id}` | Bearer | `200` / `204` |
| `GET` | `/saldo` | Bearer | `{totalReceitas, totalDespesas, totalTransferencias, saldo}` — consolidado; `?contaId=` restringe a uma conta e `?dataInicio=`/`?dataFim=` a um período |
| `DELETE` | `/sessoes` | Bearer | `204` — encerra todas as sessões do usuário |

Todo recurso é filtrado pelo usuário autenticado, extraído do token — **nunca** de parâmetro da
requisição.

As datas de `/saldo` seguem o contrato das de `/transacoes`: inclusivas, opcionais e
independentes entre si. Com período, os totais e o `saldo` descrevem só o que aconteceu nele —
o `saldo` passa a ser o resultado do período, e não o acumulado até a data. As duas rotas montam
o recorte pelos mesmos predicados, então o total de um período é a soma do que a listagem desse
período devolve.

### Formato de erro

Uniforme em **todas** as respostas de erro, inclusive no `401` de quem não se autenticou:

```json
{ "momento": "2026-08-20T18:28:41.707Z", "status": 400, "mensagem": "Dados inválidos",
  "erros": { "usuario": "O usuário é obrigatório" } }
```

`400` validação, corpo ilegível ou parâmetro de tipo errado · `401` credenciais ou token ·
`404` não encontrado ou rota inexistente · `405` método não permitido (com `Allow`) ·
`409` conflito · `415` formato não suportado · `429` limite excedido.

O mapa `erros` só vem preenchido na validação de borda, e é o que permite ao cliente apontar o
campo culpado. Erros de cliente têm mensagem fixa: o detalhe da exceção fica no log, que é onde
tem leitor legítimo.

---

## Segurança

- **JWT** HS384, expiração de 24h, stateless. Claims: `sub` (id) e `usuario`.
- **Sem cadastro aberto**: a conta nasce por `db/criar-usuario.sql`, não pela API. O que a rota de
  cadastro oferecia sem verificar e-mail era acesso a quem chegasse ao endereço.
- **Senhas** em BCrypt, mínimo de 10 caracteres e teto de 72 bytes (limite do algoritmo). A regra
  hoje é conferida pelo script de criação, único caminho por onde uma senha entra. Sem regra de
  composição, conforme o NIST SP 800-63B.
- **Revogação de sessões**: `DELETE /api/sessoes` move `usuarios.sessoes_validas_apos` para agora
  e derruba todo token já emitido, inclusive o de quem pediu. A emissão viaja no token em
  milissegundos, e não no `iat` — segundos não decidem o empate entre revogar e reautenticar no
  mesmo instante.
- **Menor privilégio no banco**: ver a seção de configuração.
- **Nome de usuário canônico**: minúsculas e sem espaços nas pontas, na borda e reforçado por
  `CHECK`. A mesma regra vale para o login e para a chave da trava por conta — definições
  divergentes a tornariam contornável pela caixa das letras. O `CHECK` pesa mais desde que a conta
  é inserida à mão: um nome gravado fora da forma canônica seria uma conta que o login não acha.
- **Login em tempo constante**: quando a conta não existe, a verificação corre contra um hash
  descartável antes de recusar, para que o relógio não revele quem tem conta.
- **Trilha de auditoria** em logger próprio (`AUDITORIA`), em `logs/`: login aceito, recusado e
  bloqueado, encerramento de sessões e limite por origem. A senha nunca entra.
- **CORS** restrito a `http://localhost:5173`, **sem** `allowCredentials` — a autenticação vai no
  cabeçalho `Authorization`, não em cookie.

### Limite de requisições

Token bucket (Bucket4j + Caffeine) no login. Ajuste em `application.yml`:

```yaml
gefinx:
  limite-requisicoes:
    login:            { tentativas: 5,  janela: 1m }
    login-por-conta:  { tentativas: 10, janela: 15m }
```

Excedido → `429` com `Retry-After`. São dois limites diferentes: um **por origem**, que não
alcança ataque distribuído, e um **por conta alvo**, que zera a cada login bem-sucedido.

Três limitações a considerar antes de publicar:

1. Os contadores vivem **em memória** — cada instância aplicaria o limite isoladamente. Ao escalar
   horizontalmente, migre para Redis ou Hazelcast, que o Bucket4j suporta.
2. A origem vem de `getRemoteAddr()`, e não de `X-Forwarded-For`, que o cliente pode forjar. Atrás
   de proxy, configure `server.forward-headers-strategy`.
3. A trava por conta é, por construção, um vetor de negação de serviço: quem souber o nome de
   usuário de alguém mantém a conta bloqueada gastando dez requisições. A janela é curta e o
   contador zera no login bem-sucedido, mas falta a terceira saída — um fluxo de recuperação de
   senha, que hoje é um `UPDATE` no banco pelo mesmo caminho que criou a conta.

---

## Manutenção do banco

Os scripts em `db/` rodam como superusuário, fora do ciclo do Flyway:

| Script | Quando |
|---|---|
| `criar-roles.sql` | Antes da primeira subida, sempre |
| `renomear-para-gefinx.sql` | Só em instalação anterior a agosto/2026, que ainda use os nomes `financas_*` |

---

## Limitações conhecidas

- **Sem varredura de CVE**: o `dependency-check` exige chave de API da NVD, e manter credencial
  externa viva não se pagava num projeto que não saía da máquina. O que resta é
  `mvn versions:display-dependency-updates`, que cobre frescor e não vulnerabilidade. **Publicar
  muda essa conta**: uma CVE em dependência exposta à internet é explorável, e esta é a primeira
  linha a revisitar antes do deploy.
- **Sem OpenAPI/Swagger nem CI.** O Actuator entrou na Etapa 22, mas só pelo health check.
- **Limite de requisições e sessões em memória** (Caffeine): valem por instância e se perdem no
  reinício. Com mais de uma instância, migrar para armazenamento compartilhado.
- **Token em `localStorage`** no cliente, exposto a XSS. A alternativa — cookie `httpOnly` —
  mudaria o desenho da autenticação.
- **Sem refresh token**: a sessão dura 24h e acaba.
