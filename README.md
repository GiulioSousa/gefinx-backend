# Gerenciador Financeiro — Backend

API REST do gerenciador financeiro pessoal, em Spring Boot 4 com arquitetura orientada a domínio (DDD).

## Requisitos

- Java 21+ (desenvolvido com JDK 25)
- Maven 3.9+
- PostgreSQL 16+ rodando localmente

## Configuração

1. Crie o banco de dados:

   ```bash
   psql -U postgres -p 5433 -c "CREATE DATABASE financas_db WITH ENCODING 'UTF8';"
   ```

2. Copie o modelo de configuração e preencha com os valores reais:

   ```bash
   cp src/main/resources/application-example.yml src/main/resources/application-local.yml
   ```

   `application-local.yml` é ignorado pelo Git e guarda a senha do banco e a chave JWT.
   Gere uma chave nova com:

   ```bash
   node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
   ```

3. Suba a aplicação — o Flyway aplica as migrations automaticamente:

   ```bash
   mvn spring-boot:run
   ```

   A API fica em `http://localhost:8080/api`.

Em produção, em vez de `application-local.yml`, defina `SPRING_PROFILES_ACTIVE` e as variáveis
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` e `JWT_EXPIRACAO_MINUTOS`.

### Limite de requisições

As rotas de autenticação são protegidas contra força bruta por endereço de origem, com token
bucket (Bucket4j). Ajuste em `application.yml`:

```yaml
financas:
  limite-requisicoes:
    login:    { tentativas: 5, janela: 1m }
    registro: { tentativas: 3, janela: 10m }
```

Ao exceder, a API responde `429` com o cabeçalho `Retry-After`.

Duas limitações a considerar antes de publicar: os contadores vivem **em memória**, então cada
instância aplicaria o limite isoladamente — ao escalar horizontalmente, migre para um armazenamento
compartilhado (o Bucket4j tem adaptadores para Redis e Hazelcast). E o endereço de origem vem de
`getRemoteAddr()`; atrás de um proxy reverso, configure `server.forward-headers-strategy` para que
o endereço real seja resolvido pelo contêiner, em vez de confiar em cabeçalhos que o cliente pode
forjar.

## Arquitetura

Organização por **bounded context**, cada um em quatro camadas:

```
com.financas.backend
├── usuarios/        # identidade e autenticação
├── financas/        # categorias, transações e saldo
└── compartilhado/   # segurança, CORS e tratamento global de erros
```

Dentro de cada contexto:

| Camada | Responsabilidade |
|---|---|
| `dominio` | Entidades e portas de repositório, sem dependência de Spring ou JPA |
| `aplicacao` | Casos de uso, orquestrando as portas do domínio |
| `infraestrutura` | Adaptadores JPA e serviços técnicos (JWT) |
| `interfaces/web` | Controllers REST e DTOs |

As fronteiras entre contextos são propositais: `usuarios` e `financas` são candidatos a extração
como serviços independentes.

## Endpoints

| Método | Rota | Autenticação |
|---|---|---|
| `POST` | `/api/auth/registrar` | pública |
| `POST` | `/api/auth/login` | pública |
| `GET` `POST` | `/api/categorias` | Bearer |
| `PUT` `DELETE` | `/api/categorias/{id}` | Bearer |
| `GET` `POST` | `/api/transacoes` | Bearer |
| `PUT` `DELETE` | `/api/transacoes/{id}` | Bearer |
| `GET` | `/api/saldo` | Bearer |

Todos os recursos são filtrados pelo usuário autenticado, extraído do token — nunca de parâmetro
da requisição.
