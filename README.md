# GeFinX — Backend

API REST do **GeFinX**, um gerenciador financeiro pessoal: o usuário cadastra receitas e despesas
classificadas por categoria e acompanha o saldo. Multiusuário — cada conta enxerga apenas os
próprios dados.

Spring Boot 4 com arquitetura orientada a domínio (DDD), organizada por *bounded context*. A
interface web vive em repositório próprio, `gefinx-frontend`, e essa separação é deliberada: o
objetivo de médio prazo é extrair alguns contextos como serviços independentes.

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

### 3. Preencha a configuração local

```bash
cp src/main/resources/application-example.yml src/main/resources/application-local.yml
```

`application-local.yml` é ignorado pelo Git e guarda as duas senhas do banco e a chave JWT.
Gere uma chave nova (Base64, mínimo 48 bytes para HS384) com:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
```

### 4. Suba

```bash
mvn spring-boot:run
```

O Flyway aplica as migrations `V1`–`V6` automaticamente. A API fica em
`http://localhost:8080/api`.

### Em produção

Em vez de `application-local.yml`, defina `SPRING_PROFILES_ACTIVE` e as variáveis `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRACAO_USERNAME`, `DB_MIGRACAO_PASSWORD`, `JWT_SECRET` e
`JWT_EXPIRACAO_MINUTOS`.

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

O acoplamento entre contextos é deliberadamente mínimo. A única travessia é `RegistroUseCase`, que
cria o usuário e suas categorias padrão — consciente e documentada, por ser o ponto que precisará
virar comunicação entre serviços numa extração futura.

### Modelo de dados

| Tabela | Observações |
|---|---|
| `usuarios` | Senha em BCrypt; `CHECK (email = lower(email))`; `sessoes_validas_apos` |
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
| `POST` | `/auth/registrar` | pública | `201` + token |
| `POST` | `/auth/login` | pública | `200` + token |
| `GET` `POST` | `/categorias` | Bearer | lista / `201` |
| `PUT` `DELETE` | `/categorias/{id}` | Bearer | `200` / `204` |
| `GET` `POST` | `/transacoes` | Bearer | lista / `201` |
| `PUT` `DELETE` | `/transacoes/{id}` | Bearer | `200` / `204` |
| `GET` | `/saldo` | Bearer | `{totalReceitas, totalDespesas, saldo}` |
| `DELETE` | `/sessoes` | Bearer | `204` — encerra todas as sessões do usuário |

Todo recurso é filtrado pelo usuário autenticado, extraído do token — **nunca** de parâmetro da
requisição.

### Formato de erro

Uniforme em **todas** as respostas de erro, inclusive no `401` de quem não se autenticou:

```json
{ "momento": "2026-08-20T18:28:41.707Z", "status": 400, "mensagem": "Dados inválidos",
  "erros": { "senha": "A senha deve ter no mínimo 10 caracteres" } }
```

`400` validação, corpo ilegível ou parâmetro de tipo errado · `401` credenciais ou token ·
`404` não encontrado ou rota inexistente · `405` método não permitido (com `Allow`) ·
`409` conflito · `415` formato não suportado · `429` limite excedido.

O mapa `erros` só vem preenchido na validação de borda, e é o que permite ao cliente apontar o
campo culpado. Erros de cliente têm mensagem fixa: o detalhe da exceção fica no log, que é onde
tem leitor legítimo.

---

## Segurança

- **JWT** HS384, expiração de 24h, stateless. Claims: `sub` (id), `email`, `nome`.
- **Senhas** em BCrypt, mínimo de 10 caracteres, teto de 72 bytes (limite do algoritmo) e recusa
  das mais previsíveis. Sem regra de composição, conforme o NIST SP 800-63B.
- **Revogação de sessões**: `DELETE /api/sessoes` move `usuarios.sessoes_validas_apos` para agora
  e derruba todo token já emitido, inclusive o de quem pediu. A emissão viaja no token em
  milissegundos, e não no `iat` — segundos não decidem o empate entre revogar e reautenticar no
  mesmo instante.
- **Menor privilégio no banco**: ver a seção de configuração.
- **E-mail canônico**: minúsculas e sem espaços nas pontas, na borda e reforçado por `CHECK`. A
  mesma regra vale para cadastro, login e para a chave da trava por conta — definições divergentes
  a tornariam contornável pela caixa das letras.
- **Login em tempo constante**: quando o e-mail não existe, a verificação corre contra um hash
  descartável antes de recusar, para que o relógio não revele quem tem conta.
- **Trilha de auditoria** em logger próprio (`AUDITORIA`), em `logs/`: login aceito, recusado e
  bloqueado, cadastro, encerramento de sessões e limite por origem. A senha nunca entra.
- **CORS** restrito a `http://localhost:5173`, **sem** `allowCredentials` — a autenticação vai no
  cabeçalho `Authorization`, não em cookie.

### Limite de requisições

Token bucket (Bucket4j + Caffeine) nas rotas de autenticação. Ajuste em `application.yml`:

```yaml
gefinx:
  limite-requisicoes:
    login:            { tentativas: 5,  janela: 1m }
    login-por-conta:  { tentativas: 10, janela: 15m }
    registro:         { tentativas: 3,  janela: 10m }
```

Excedido → `429` com `Retry-After`. São dois limites diferentes: um **por origem**, que não
alcança ataque distribuído, e um **por conta alvo**, que zera a cada login bem-sucedido.

Três limitações a considerar antes de publicar:

1. Os contadores vivem **em memória** — cada instância aplicaria o limite isoladamente. Ao escalar
   horizontalmente, migre para Redis ou Hazelcast, que o Bucket4j suporta.
2. A origem vem de `getRemoteAddr()`, e não de `X-Forwarded-For`, que o cliente pode forjar. Atrás
   de proxy, configure `server.forward-headers-strategy`.
3. A trava por conta é, por construção, um vetor de negação de serviço: quem souber o e-mail de
   alguém mantém a conta bloqueada gastando dez requisições. A janela é curta e o contador zera no
   login bem-sucedido, mas falta a terceira saída — um fluxo de recuperação de senha.

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
  externa viva não se paga aqui. O que resta é `mvn versions:display-dependency-updates`, que
  cobre frescor e não vulnerabilidade.
- **Sem OpenAPI/Swagger, Actuator ou CI.**
- **Token em `localStorage`** no cliente, exposto a XSS. A alternativa — cookie `httpOnly` —
  mudaria o desenho da autenticação.
- **Sem refresh token**: a sessão dura 24h e acaba.
