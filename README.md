# Aisly AuthServer

Pós-Graduação em Desenvolvimento de Aplicativos Móveis da PUC-PR  
Disciplina: Serviços Mobile em Cloud AWS (Turma U)  
Professor: Vinícius Godoy Mendonça  
Aluno: Levi Lunique Izidio da Silva

Servidor de autenticação do projeto **Aisly**, em Kotlin + Spring Boot 4. Emite e
valida tokens **JWT (HMAC-SHA, HS256)** e é a única autoridade de login do
ecossistema: o [aisly-backend](https://github.com/LeviLunique/aisly-backend)
atua como _resource server_ e confia nos tokens emitidos aqui (mesmo `secret`
HMAC compartilhado).

Construído a partir do esqueleto da disciplina e espelha a estrutura de
referência do professor (pacotes `users` / `roles` / `security` / `exceptions`),
com dois desvios conscientes para o Aisly: **senha com hash PBKDF2** (em vez de
texto puro) e **webhook de exclusão de conta** para o aisly-backend. Segue o
padrão da disciplina — **JPA + perfis Spring**: `local` roda em **H2** em memória
(nenhum banco necessário) e `dev` persiste em **PostgreSQL**.

## Integrantes

- Levi Lunique <!-- ajuste nome completo/RA conforme necessário -->

## Papel no ecossistema Aisly

```
┌─────────────┐  criar / login      ┌───────────────────┐
│  Aisly iOS  │ ──────────────────► │  Aisly AuthServer │  emite JWT HS256
│             │ ◄──── JWT ───────── │      :8080 /api   │  (secret HMAC compartilhado)
└─────────────┘                     └─────────┬─────────┘
       │ Bearer JWT                            │ ao excluir a conta (DELETE /users/{id})
       ▼                                       │ chama o webhook interno
┌──────────────────────┐                       ▼
│   aisly-backend :8081 │ ◄── valida o JWT com o mesmo secret HMAC
│  (resource server)    │ ◄── POST /internal/v1/users/{sub}/delete-data
└──────────────────────┘
```

## Estrutura

```
br.pucpr.authserver
├── AuthserverApplication.kt      @ConfigurationPropertiesScan
├── Bootstrapper.kt               cria roles ADMIN/PREMIUM e o admin inicial no boot
├── RootController.kt             GET /healthcheck
├── integration/                 ponte com o aisly-backend (única parte fora do baseline do professor)
│   ├── BackendProperties.kt      @ConfigurationProperties("aisly")
│   └── AccountDeletionNotifier.kt   webhook de exclusão de conta (Tema 3)
├── exceptions/                   BadRequest / Forbidden / NotFound / Unauthorized
├── roles/                        Role, Controller, Service, Repository, requests/responses
├── security/
│   ├── JWT.kt                    emissão/validação HMAC (JJWT)
│   ├── JwtTokenFilter.kt         extrai o Bearer e popula o SecurityContext
│   ├── SecurityConfig.kt         filter chain stateless + CORS + regras de acesso
│   ├── SecurityProperties.kt     @ConfigurationProperties("security")
│   ├── UserToken.kt              claim `user` (id, name, roles)
│   └── PasswordHasher.kt         PBKDF2 (desvio Aisly)
└── users/                        User, Controller, Service, Repository, requests/responses, SortDir
```

## Endpoints

Context-path: **`/api`**. Todos os paths abaixo já incluem o prefixo.

| Método | Path | Auth | Descrição |
| --- | --- | --- | --- |
| POST | `/api/users` | público | Cria usuário `{email, password, name}` → `201 UserResponse` |
| POST | `/api/users/login` | público | Login `{email, password}` → `200 LoginResponse {token, user}` |
| GET | `/api/users` | público | Lista usuários (`?sortDir=ASC|DESC`, `?role=`) |
| GET | `/api/users/{id}` | público | `200 UserResponse` |
| PATCH | `/api/users/{id}` | Bearer (self ou ADMIN) | Atualiza `{name}` |
| DELETE | `/api/users/{id}` | Bearer (**self ou ADMIN**) | Exclui a conta; antes aciona o webhook `delete-data` |
| PUT | `/api/users/{id}/roles/{role}` | Bearer (ADMIN) | Concede papel |
| POST | `/api/roles` | Bearer (ADMIN) | Cria papel `{name, description}` |
| GET | `/api/roles` | Bearer (autenticado) | Lista papéis |
| GET | `/api/healthcheck` | público | Health check |

`UserResponse = {id, email, name}` · `LoginResponse = {token, user: UserResponse}`

## Token JWT

- Algoritmo: **HS256** (HMAC-SHA), assinado com o `security.secret` compartilhado.
- `issuer`: `Aisly AuthServer` · `subject`: id do usuário.
- Claim `user`: `{ id, name, roles }` (usado para autorização self/admin).
- Expiração: `expireHours` (48h) para usuários, `adminExpireHours` (1h) para admin.
- O aisly-backend valida os tokens com o **mesmo `secret`**.

## Como rodar

**Local (H2, sem banco)** — perfil `local`:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun    # porta 8080
curl localhost:8080/api/healthcheck

# Criar usuário → 201
curl -s -X POST localhost:8080/api/users \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@aisly.dev","password":"s3cret@Pass","name":"Demo"}'

# Login → token
curl -s -X POST localhost:8080/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@aisly.dev","password":"s3cret@Pass"}'
```

Swagger UI: http://localhost:8080/api/swagger-ui.html · OpenAPI JSON:
http://localhost:8080/api/v3/api-docs

**Com PostgreSQL (perfil `dev`)** — persiste entre reinícios:

```bash
docker run -d --name aisly-pg -e POSTGRES_DB=aisly \
  -e POSTGRES_USER=aisly -e POSTGRES_PASSWORD=aisly -p 5432:5432 postgres:18-alpine

SPRING_PROFILES_ACTIVE=dev DB_HOST=localhost DB_USER=aisly DB_PASSWORD=aisly \
  ./gradlew bootRun
```

## Configuração

| Variável | Default | Uso |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | — | `local` = H2 em memória; `dev` = PostgreSQL |
| `DB_HOST` / `DB_PORT` / `DB_SCHEMA` | `localhost` / `5432` / `aisly` | Datasource PostgreSQL (perfil `dev`) |
| `DB_USER` / `DB_PASSWORD` | `aisly` / `aisly` | Credenciais PostgreSQL (perfil `dev`) |
| `AISLY_BASE_URL` | _(vazio)_ | Base do aisly-backend para o webhook de exclusão (vazio = desativa) |
| `AISLY_INTERNAL_WEBHOOK_SECRET` | _(vazio)_ | Segredo enviado como header `X-Aisly-Internal-Secret` |
| `AISLY_SECURITY_HMAC_SECRET` | — | Segredo HMAC dos JWTs; deve ser o mesmo do aisly-backend |
| `AISLY_BOOTSTRAP_ADMIN_EMAIL` / `AISLY_BOOTSTRAP_ADMIN_PASSWORD` | _(vazio)_ | Cria o admin inicial somente quando ambos forem informados |

Config local opcional em `src/main/resources/security.yaml`, ignorado pelo Git.
Use `src/main/resources/security.example.yaml` como referência e injete os
valores reais por variável de ambiente, GitHub Secrets, AWS SSM ou Secrets
Manager.

## Segurança

- **Senhas: PBKDF2** (SHA-256, 120k iterações, salt por senha, formato
  `base64(salt):base64(hash)`). Melhoria sobre o texto puro do baseline do
  professor — a senha nunca é persistida em claro.
- **Tokens: JWT HS256** com segredo HMAC compartilhado com o aisly-backend.
- **Exclusão de conta** (Tema 3): `DELETE /api/users/{id}` permite auto-exclusão
  (o próprio usuário) ou ADMIN. Antes de apagar, aciona o webhook
  `POST {AISLY_BASE_URL}/internal/v1/users/{id}/delete-data` do aisly-backend.
  Falha do webhook **aborta** a exclusão (safe retry, sem dados órfãos). Com
  `AISLY_BASE_URL` vazio o webhook é pulado (log de warn) — modo de
  desenvolvimento local.

## Testes

```bash
./gradlew test
```

Testes de integração (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")`)
cobrem criação, login (sucesso/senha errada), consulta, auto-exclusão (com login
posterior falhando) e a criação do admin pelo Bootstrapper usando propriedades
de teste não secretas.

## Persistência

- **`local`** → H2 em memória. Zera a cada boot; ideal para dev e testes.
- **`dev`** → PostgreSQL (`ddl-auto: update`). Cadastros sobrevivem a reinícios;
  é o perfil de deploy (o container `auth` fala com o mesmo PostgreSQL do
  aisly-backend). O esquema é gerado pelo Hibernate.

## Deploy e infraestrutura

O AuthServer **não tem infra própria**. Ele compartilha a AWS provisionada pelo
[aisly-backend](https://github.com/LeviLunique/aisly-backend) (Terraform em
`infra/terraform/`): uma EC2 rodando um stack Docker Compose com três
containers — `postgres`, `auth` (este servidor) e `api` (aisly-backend) — e um
bucket S3 de deploy.

Dois mecanismos distintos, que **não** se misturam:

- **Terraform** (no repo aisly-backend) provisiona a infra **uma vez**. No boot,
  o `user-data` baixa `authserver.jar` do S3 e sobe o container `auth` com o
  perfil `dev` apontando para o PostgreSQL compartilhado.
- **CI** (`.github/workflows/ci.yml`, neste repo) faz o **re-deploy contínuo**:
  a cada push em `develop`/`main`, builda o jar, sobe para o **mesmo bucket S3**
  e reinicia o container `auth` via SSM (`docker-compose up -d --force-recreate
  auth`) + healthcheck `/api/healthcheck`.

Ou seja: os dois repositórios têm CIs **independentes** que empurram cada um o
seu jar para a **mesma infra compartilhada** — não há pipeline único nem
Terraform no CI.

### Pré-requisitos do CI (configurar uma vez no GitHub)

1. Secret `AWS_DEPLOY_ROLE_ARN` neste repositório (o ARN da role de deploy OIDC,
   a mesma usada pelo aisly-backend).
2. A **trust policy** dessa role IAM precisa autorizar **também** este
   repositório no `sub` do OIDC (ex.: `repo:LeviLunique/aisly-authserver:*`),
   além do aisly-backend. As permissões (S3 PutObject, SSM SendCommand, EC2
   Describe) já estão na role e são reaproveitadas.
