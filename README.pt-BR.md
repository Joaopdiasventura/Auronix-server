# Auronix Server

[English](README.md)

<a id="table-of-contents"></a>

## Sumário

- [Visão Geral do Projeto](#project-overview)
- [Arquitetura do Sistema](#system-architecture)
- [Stack Tecnológica](#tech-stack)
- [Domínio e Conceitos Centrais](#domain-core-concepts)
- [Detalhes de Implementação](#implementation-details)
- [Decisões de Engenharia & Trade-offs](#engineering-decisions-trade-offs)
- [Considerações de Performance](#performance-considerations)
- [Considerações de Segurança](#security-considerations)
- [Escalabilidade & Confiabilidade](#scalability-reliability)
- [Configuração de Desenvolvimento](#development-setup)
- [Execução do Projeto](#running-the-project)
- [Estratégia de Testes](#testing-strategy)
- [Observabilidade](#observability)
- [Referência de API](#api-reference)
- [Roadmap / Melhorias Futuras](#roadmap-future-improvements)

<a id="project-overview"></a>

## Visão Geral do Projeto

O Auronix Server é um monólito modular em NestJS que expõe uma API HTTP para gerenciamento de contas, transferências internas de valor armazenado, cobranças com expiração e entrega de notificações em tempo real via Server-Sent Events (SSE). O PostgreSQL é a fonte de verdade para os dados de negócio duráveis. O Redis sustenta as filas BullMQ, os buffers de replay de notificações e o pub/sub entre instâncias.

A principal proposta técnica é oferecer comportamento transacional previsível para movimentação de saldo em estilo carteira digital sem introduzir a sobrecarga de coordenação entre microserviços. As transferências são aceitas de forma síncrona, liquidadas de forma assíncrona e protegidas contra gasto duplo por meio de locking no banco de dados durante o caminho do worker.

<a id="system-architecture"></a>

## Arquitetura do Sistema

A aplicação é organizada como um monólito modular. Controllers HTTP, serviços de domínio, processors de fila e adapters de infraestrutura são agrupados em módulos NestJS e implantados como um único runtime. Isso favorece simplicidade transacional e velocidade de implementação em vez de isolamento por serviço.

As responsabilidades de API e worker hoje estão co-localizadas no mesmo processo. Isso permite escala horizontal, mas a capacidade de web e de workers é ampliada em conjunto, e não de forma independente.

```mermaid
flowchart TD
  Client[Browser / Cliente de API]
  SSE[Consumidores SSE]

  subgraph App["Auronix Server (monólito modular em NestJS)"]
    Fastify[Adapter HTTP Fastify]

    subgraph Core["Módulos de negócio"]
      UserModule[Módulo de usuário]
      TransferModule[Módulo de transferência]
      PaymentRequestModule[Módulo de cobrança]
    end

    subgraph Shared["Módulos compartilhados"]
      AuthModule[Módulo de autenticação]
      DatabaseModule[Módulo de banco]
      CacheModule[Módulo de cache Redis]
      NotificationModule[Módulo de notificações]
      Workers[Processors BullMQ]
    end
  end

  Postgres[(PostgreSQL)]
  Redis[(Redis / BullMQ / PubSub)]

  Client --> Fastify
  Fastify --> UserModule
  Fastify --> TransferModule
  Fastify --> PaymentRequestModule
  Fastify --> NotificationModule

  UserModule --> AuthModule
  TransferModule --> DatabaseModule
  TransferModule --> NotificationModule
  TransferModule --> Workers
  PaymentRequestModule --> Workers
  NotificationModule --> CacheModule
  Workers --> CacheModule

  DatabaseModule --> Postgres
  CacheModule --> Redis
  NotificationModule --> SSE
```

| Componente             | Responsabilidade                                                                           | Observações                                                                     |
| ---------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| `UserModule`           | Cadastro, login, renovação de sessão, atualização de perfil e exclusão                     | Define e limpa `auth_cookie` nos fluxos de autenticação bem-sucedidos           |
| `TransferModule`       | Criação, consulta individual, histórico paginado e liquidação assíncrona de transferências | Usa BullMQ e lock pessimista para preservar a correção do saldo                 |
| `PaymentRequestModule` | Criação de cobrança e limpeza assíncrona por expiração                                     | Cobranças expiradas tornam-se indisponíveis antes da execução do job de limpeza |
| `NotificationModule`   | Stream SSE, busca de replay e fan-out via Redis pub/sub                                    | Mantém registries de conexões em memória por usuário e por processo             |
| `DatabaseModule`       | Abstração transacional sobre TypeORM/PostgreSQL                                            | A liquidação de transferências ocorre em uma única transação de banco           |
| `CacheModule`          | Acesso ao Redis para filas, replay, contadores e pub/sub                                   | O Redis não é usado como cache geral de leitura de dados de negócio             |

### Sequência de Processamento de Transferência

```mermaid
sequenceDiagram
  participant Client
  participant API as TransferController/Service
  participant DB as PostgreSQL
  participant Q as Fila BullMQ de Transferência
  participant Worker as TransferProcessor
  participant NQ as Fila de Notificações
  participant Redis as Redis
  participant SSE as Notification Gateway

  Client->>API: POST /transfer
  API->>DB: Transação: valida pagador/destinatário, checa saldo, insere transferência pendente
  DB-->>API: transferência pendente
  API->>Q: enfileira job de processamento
  API-->>Client: 201 Created (status=pending)

  Q->>Worker: consome job de transferência
  Worker->>DB: Transação: bloqueia linhas do pagador/destinatário, revalida saldo
  Worker->>DB: atualiza saldos e marca transferência como concluída
  Worker->>NQ: enfileira jobs de notificação
  NQ->>Redis: publica payloads de notificação + grava lista de replay
  Redis-->>SSE: evento via pub/sub
  SSE-->>Client: transfer.completed / transfer.failed
```

| Estilo arquitetural   | Escolha atual                             | Motivo                                                                                                                   |
| --------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Unidade de deploy     | Serviço único em NestJS                   | Mantém os limites de consistência locais e remove coordenação entre serviços no fluxo central de movimentação financeira |
| Trabalho assíncrono   | Workers BullMQ dentro da aplicação        | Desacopla liquidação de transferências e expiração de cobranças sem introduzir outro codebase de serviço                 |
| Entrega em tempo real | SSE sobre sessões autenticadas por cookie | Ajusta-se bem a streaming unidirecional com integração mais simples em navegador do que WebSockets                       |

<a id="tech-stack"></a>

## Stack Tecnológica

| Tecnologia                          | Versão / família                        | Onde é usada                                                   | Justificativa                                                                                                     |
| ----------------------------------- | --------------------------------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Node.js                             | `24.x`                                  | Runtime e imagem base do container                             | Base de runtime atual do repositório; recursos modernos da linguagem e boa sustentação para workloads assíncronos |
| TypeScript                          | `5.7.x`                                 | Codebase inteira                                               | Tipagem forte para limites de serviço, DTOs, repositórios e helpers de teste                                      |
| NestJS                              | `11.x`                                  | Framework da aplicação                                         | Sistema de módulos, DI, suporte a testes, integração com BullMQ e estrutura consistente de controller/service     |
| Adapter Fastify                     | Plataforma Nest Fastify                 | Servidor HTTP                                                  | Overhead menor do que Express e ecossistema suficiente para este serviço                                          |
| TypeORM                             | `0.3.x`                                 | Entidades, repositórios, transações                            | Integração prática com Nest e suporte adequado a entidades/repositórios em um monólito modular pequeno            |
| PostgreSQL                          | `16` no compose                         | Fonte de verdade                                               | Integridade transacional, row locking, índices e estado durável para saldos e transferências                      |
| Redis                               | `7` no compose                          | Backend do BullMQ, replay de notificações, pub/sub, contadores | Infraestrutura compartilhada e de baixa latência para filas e fan-out de eventos                                  |
| BullMQ                              | `5.x`                                   | Workers de transferência, notificação e cobrança               | Retries, delayed jobs, controle de concorrência e modelo operacional nativo em Redis                              |
| JWT                                 | Módulo JWT do Nest                      | Geração e validação de tokens de sessão                        | Payload de sessão compacto e stateless armazenado em cookie HTTP-only seguro                                      |
| Argon2id                            | Pacote `argon2`                         | Hash de senha                                                  | Algoritmo moderno de hashing de senha com custos configuráveis e suporte a pepper                                 |
| class-validator / class-transformer | Versões compatíveis com Nest            | Validação de DTO e transformação de query                      | Rejeita payloads inválidos e campos fora da whitelist na borda da aplicação                                       |
| Jest + Supertest                    | Versões atuais do repositório           | Testes unitários e e2e                                         | Toolchain padrão do ecossistema Nest com cobertura adequada em nível HTTP                                         |
| Docker / Compose                    | Dockerfile multi-stage + `compose.yaml` | Orquestração local em container                                | Runtime local reproduzível para app, PostgreSQL e Redis                                                           |

<a id="domain-core-concepts"></a>

## Domínio e Conceitos Centrais

Todos os valores monetários são armazenados como centavos inteiros, e não como decimais de ponto flutuante. Esse é o invariante base por trás de saldos, transferências e cobranças.

| Entidade / conceito     | Finalidade                                             | Campos importantes                                                  |
| ----------------------- | ------------------------------------------------------ | ------------------------------------------------------------------- |
| `User`                  | Titular da conta e portador do saldo                   | `id`, `email`, `name`, `password`, `balance`, timestamps            |
| `Transfer`              | Movimentação de razão entre dois usuários              | `payer`, `payee`, `value`, `status`, `failureReason`, `completedAt` |
| `PaymentRequest`        | Cobrança de curta duração para um valor específico     | `id`, `value`, `user`, `expiresAt`, `createdAt`                     |
| Evento de notificação   | Sinal em tempo real sobre o resultado da transferência | `id`, `userId`, `type`, `data`, `occurredAt`                        |
| Status de transferência | Estado do ciclo de vida da transferência               | `pending`, `completed`, `failed`                                    |

### Invariantes atuais

- Cada novo usuário inicia com saldo de `100000` centavos.
- Transferências são criadas como `pending` e liquidadas de forma assíncrona por um worker BullMQ.
- Auto-transferências são rejeitadas antes da persistência.
- A suficiência de saldo é verificada duas vezes: antes da criação do registro e novamente durante a liquidação assíncrona sob row locks.
- Conclusão e falha de transferência geram eventos de notificação para entrega via SSE.
- Cobranças expiram após 10 minutos.
- `GET /payment-request/:id` retorna `404` quando a cobrança está expirada, mesmo antes de o job atrasado remover a linha.
- A paginação do histórico de transferências é baseada em cursor e ordenada por `(completedAt DESC, id DESC)`.
- `GET /transfer` retorna apenas transferências concluídas; transferências pendentes e com falha ficam disponíveis por identificador, não pela listagem histórica.

```mermaid
stateDiagram-v2
  [*] --> Pending
  Pending --> Completed: liquidação pelo worker com sucesso
  Pending --> Failed: falha ao enfileirar / erro irrecuperável / retries esgotados
  Completed --> [*]
  Failed --> [*]
```

<a id="implementation-details"></a>

## Detalhes de Implementação

### Estrutura de pastas

| Caminho                | Papel                                                                     |
| ---------------------- | ------------------------------------------------------------------------- |
| `src/main.ts`          | Bootstrap do Fastify, Helmet, CORS, validation pipe e registro de cookies |
| `src/app.module.ts`    | Composição de módulos no topo da aplicação, TypeORM, BullMQ e throttling  |
| `src/core/*`           | Módulos de negócio: usuário, transferência e cobrança                     |
| `src/shared/modules/*` | Infraestrutura transversal: auth, cache, banco e notificações             |
| `src/config/*`         | Loaders e tipos de configuração derivados do ambiente                     |
| `test/e2e/*`           | Cenários end-to-end usando PostgreSQL e Redis reais                       |

<details>
<summary>Trecho da árvore do repositório</summary>

```text
src/
  config/
  core/
    payment-request/
    transfer/
    user/
  shared/
    dto/
    guards/
    http/
    modules/
      auth/
      cache/
      database/
      notification/
  app.controller.ts
  app.module.ts
  main.ts
test/
  e2e/
    support/
Dockerfile
compose.yaml
package.json
```

</details>

### Padrões de módulo e de código

| Padrão                                              | Onde aparece                                                          | Por que importa                                                                   |
| --------------------------------------------------- | --------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Controllers enxutos                                 | Controllers de negócio e de notificação                               | Validação e transporte ficam na borda; a orquestração fica nos services           |
| Abstração de repositório via tokens de DI           | `IUserRepository`, `ITransferRepository`, `IPaymentRequestRepository` | Mantém os services desacoplados das implementações específicas de TypeORM         |
| Wrapper transacional explícito                      | `IDatabaseService` e `DatabaseTransaction`                            | Torna os limites transacionais visíveis na criação e liquidação de transferências |
| Separação entre adapter de fila e processor         | `*.queue.ts` e `*.processor.ts`                                       | Separa a intenção de enfileirar da execução em background                         |
| Registry de conexões SSE em memória + Redis pub/sub | `NotificationGateway` e `NotificationService`                         | Suporta fan-out entre instâncias sem sticky sessions                              |
| Transformação de DTO para paginação por cursor      | `FindTransferDto`                                                     | Aceita cursor em JSON e o valida antes do uso no repositório                      |

### Ciclo de vida da requisição

1. Uma requisição chega ao Fastify e passa por validação global, parsing de cookies e autenticação opcional.
2. O controller delega para um service, que coordena repositórios, transações e adapters de fila.
3. O estado durável é persistido no PostgreSQL. Trabalho diferido é enviado ao BullMQ via Redis.
4. Processors finalizam tarefas em background e emitem notificações, que podem ser reexecutadas para clientes SSE reconectados.

### Ciclo de vida da transferência na prática

| Fase              | Comportamento                                                                                                                             |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Criação           | Valida pagador/destinatário, rejeita auto-transferência, checa saldo e insere a transferência `pending` dentro de uma transação           |
| Handoff para fila | Enfileira um job BullMQ determinístico usando o id da transferência como `jobId`                                                          |
| Liquidação        | O worker bloqueia as duas linhas de usuário com lock pessimista, revalida saldo, atualiza saldos e marca a transferência como `completed` |
| Caminho de falha  | Falhas de enqueue ou erros irrecuperáveis no processamento marcam a transferência como `failed` e preservam o motivo da falha             |
| Notificação       | Resultados da transferência são reenfileirados, serializados, gravados para replay no Redis e emitidos para clientes SSE conectados       |

<a id="engineering-decisions-trade-offs"></a>

## Decisões de Engenharia & Trade-offs

| Área de decisão           | Abordagem adotada                           | Benefício                                                              | Trade-off                                                                           |
| ------------------------- | ------------------------------------------- | ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Decomposição do serviço   | Monólito modular em vez de microserviços    | Mantém consistência das transferências e simplifica o deploy           | Menos fronteiras independentes de escala e isolamento                               |
| Execução de transferência | Liquidação assíncrona via BullMQ            | Absorve falhas transitórias de worker e mantém baixa a latência da API | O status final não é conhecido na resposta de `POST /transfer`                      |
| Controle de concorrência  | Row locking pessimista durante a liquidação | Proteção forte contra gasto duplo sob concorrência                     | Maior contenção de escrita sob alto volume                                          |
| Paginação de histórico    | Paginação por cursor em `(completedAt, id)` | Ordenação estável e paginação segura sob inserções                     | Estado de cliente um pouco mais complexo do que offset                              |
| Gestão de schema          | TypeORM `synchronize`                       | Iteração local rápida e bootstrap mínimo                               | Inseguro para evolução disciplinada de schema em produção; sem migrações auditáveis |
| Replay de notificações    | Lista no Redis com TTL e tamanho limitado   | Replay simples para clientes SSE reconectados                          | Durabilidade limitada à retenção no Redis, não a armazenamento permanente           |
| Topologia de deploy       | API e workers no mesmo processo             | Baixa pegada operacional                                               | Sem separação limpa entre escala web-only e worker-only                             |

<details>
<summary>Alternativas explicitamente não adotadas</summary>

| Alternativa                                                            | Por que não foi adotada no codebase atual                                                       |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Microserviços para os domínios de usuário, transferência e notificação | Exigiria coordenação distribuída em torno da mutação de saldo e bem mais maquinaria operacional |
| Paginação do histórico por offset                                      | Degrada com inserções concorrentes e se torna instável para histórico financeiro cronológico    |
| Concorrência otimista com retries por versão                           | Adiciona complexidade de retries onde row locking já resolve o problema atual de correção       |
| Event store persistente para notificações                              | Mais durável, porém materialmente mais complexo do que a necessidade atual de replay            |
| Sistema de migrações como único caminho de schema                      | É o mais indicado para produção, mas não é como o repositório está conectado hoje               |

</details>

<a id="performance-considerations"></a>

## Considerações de Performance

| Preocupação                               | Mitigação atual                                                                                         | Impacto prático                                                                     |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Correção da aritmética monetária          | Apenas centavos inteiros                                                                                | Evita deriva de ponto flutuante e erros de reconciliação                            |
| Corridas em transferências                | Lock pessimista nas duas linhas de usuário durante a liquidação                                         | Impede gasto duplo em cenários concorrentes                                         |
| Latência da requisição de transferência   | Liquidação assíncrona via BullMQ                                                                        | `POST /transfer` fica limitado a validação, persistência e handoff para fila        |
| Custo da paginação de histórico           | Paginação por cursor com ordenação composta                                                             | Melhor estabilidade de página e formato de query mais previsível do que offsets     |
| Performance de leitura em campos críticos | `users.email` único/indexado, `payment_requests.expires_at` indexado, campos de transferência indexados | Mantém limitados os caminhos primários de leitura                                   |
| Vazão de filas                            | Concorrência configurada em `10` para transferências/cobranças e `20` para notificações                 | Baseline aceitável sem exagerar no paralelismo de workers                           |
| Overhead de reconexão SSE                 | Replay limitado aos últimos `100` eventos por usuário com TTL de `24h`                                  | Controla uso de memória do Redis e suporta recuperação após desconexões curtas      |
| Complexidade de invalidação de cache      | Sem cache geral de leitura de negócio                                                                   | Modelo de consistência mais simples ao custo de mais leituras diretas no PostgreSQL |

### Gargalos conhecidos

- O banco de dados continua sendo o principal limite de escala porque a liquidação de transferências é sensível à correção e baseada em locking.
- API e workers compartilham o mesmo runtime, então workloads intensivos de CPU ou fila podem competir com o tratamento HTTP.
- O replay de notificações é limitado pela memória do Redis e intencionalmente não é durável além do TTL e do tamanho configurados.

<a id="security-considerations"></a>

## Considerações de Segurança

| Área                   | Implementação atual                                                             | Lacuna ou observação operacional                                                                 |
| ---------------------- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Autenticação           | JWT assinado pelo Nest JWT, armazenado em `auth_cookie`                         | Auth por cookie é simples para navegadores, mas exige disciplina na gestão de segredos           |
| Armazenamento de senha | Argon2id com pepper, `memoryCost=65536`, `timeCost=3`, `hashLength=32`          | Existem defaults de pepper na config e eles precisam ser sobrescritos fora do ambiente local     |
| Hardening HTTP         | Helmet com CSP, HSTS, frameguard, no-sniff e hide-powered-by                    | Boa base, mas a CSP ainda permite estilos inline                                                 |
| Validação de entrada   | `ValidationPipe` global com `whitelist`, `forbidNonWhitelisted` e transformação | Boa proteção de borda; payloads de erro ainda são retornados em português                        |
| Rate limiting          | Throttler global do Nest, `10` requisições por `60s`                            | Há apenas uma política global; não existe ajuste por rota                                        |
| CORS                   | Allowlist vem de `CLIENT_URLS`; credenciais habilitadas                         | Fluxos com cookies cross-site exigem configuração correta de origem                              |
| Configuração de cookie | `HttpOnly`, `Secure`, `SameSite=Strict`, path `/`                               | Ambientes de navegador sem HTTPS podem exigir localhost confiável ou terminação TLS              |
| Autorização            | Transferências são escopadas a pagador/destinatário                             | Não existe modelo RBAC; algumas leituras autenticadas são mais amplas do que as de transferência |
| CSRF                   | Não existe token CSRF explícito nem fluxo anti-CSRF baseado em same-site        | Lacuna importante para sessões de navegador baseadas em cookie                                   |
| Segredos               | `JWT_SECRET`, `COOKIE_SECRET`, `ARGON2_PEPPER` possuem fallback hard-coded      | Precisam ser sobrescritos em qualquer ambiente não local                                         |

### Fronteiras de autorização que merecem destaque

- `GET /transfer/:id` é restrito ao pagador ou destinatário da transferência.
- `GET /user/:email` exige autenticação, mas não é escopado à identidade do próprio chamador.
- `GET /payment-request/:id` exige autenticação, mas é orientado a identificador e não a ownership.

<a id="scalability-reliability"></a>

## Escalabilidade & Confiabilidade

| Tópico                                  | Postura atual                                                                     | Limitação                                                                                        |
| --------------------------------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Escala horizontal                       | Múltiplas instâncias podem compartilhar PostgreSQL e Redis                        | A capacidade de web e worker escala junto porque os papéis não são separados                     |
| Fan-out SSE                             | Redis pub/sub permite que cada instância emita para suas próprias conexões locais | O replay é limitado e não é durável além da retenção no Redis                                    |
| Resiliência de filas                    | BullMQ com `5` tentativas e backoff exponencial iniciando em `1000ms`             | Não existe dead-letter queue nem tópico separado de falhas                                       |
| Idempotência na camada de fila          | Jobs de transferência usam `transferId` como `jobId`                              | Isso não substitui idempotency keys ponta a ponta na borda HTTP                                  |
| Deduplicação no enqueue de notificações | O `jobId` da notificação é derivado de um hash SHA-256 do payload                 | A deduplicação por payload pode suprimir notificações idênticas repetidas por design             |
| Limpeza de cobranças                    | Job atrasado de expiração mais filtro de expiração na query                       | O momento da limpeza é best effort; a regra de visibilidade é mais forte que o timing da remoção |
| Health reporting                        | `/health` retorna uma resposta simples de liveness                                | Não existe readiness com dependências nem sinal de modo degradado                                |
| Consistência de dados                   | A liquidação de transferências é transacional dentro do PostgreSQL                | O sistema continua dependente de um único banco primário para correção                           |

### Comportamentos de confiabilidade já presentes

- Falhas finais no processamento de transferência viram estados persistidos `failed`.
- Falhas na publicação de notificações são registradas em log e toleradas parcialmente, sem derrubar o caminho da requisição.
- Cobranças expiradas ficam ocultas mesmo quando o worker de limpeza atrasada ainda não executou.
- Conexões SSE enviam heartbeat a cada 25 segundos e suportam replay com `Last-Event-ID`.

<a id="development-setup"></a>

## Configuração de Desenvolvimento

### Pré-requisitos

| Dependência                    | Versão recomendada | Finalidade                                    |
| ------------------------------ | ------------------ | --------------------------------------------- |
| Node.js                        | `24.x`             | Runtime para desenvolvimento local e build    |
| Yarn                           | Classic `1.x`      | Gerenciador de pacotes usado pelo repositório |
| PostgreSQL                     | `16+`              | Banco de dados principal                      |
| Redis                          | `7+`               | Backend de filas, pub/sub e store de replay   |
| Docker Desktop / Docker Engine | Atual              | Setup opcional em containers                  |

### Variáveis de ambiente

| Variável               | Default no código                                     | Finalidade                                       | Observação para produção                                                            |
| ---------------------- | ----------------------------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------- |
| `PORT`                 | `3000`                                                | Porta HTTP de escuta                             | Normalmente injetada pela plataforma                                                |
| `NODE_ENV`             | `development`                                         | Modo de ambiente da aplicação                    | Definir explicitamente como `production` fora do dev local                          |
| `POSTGRES_URL`         | `postgres://postgres:postgres@localhost:5432/auronix` | String de conexão do PostgreSQL                  | Apontar para PostgreSQL gerenciado e usar TLS/controles de rede conforme necessário |
| `POSTGRES_SYNCHRONIZE` | `true`                                                | Chave para sincronização de schema do TypeORM    | Definir `false` em produção e substituir por migrações reais                        |
| `REDIS_URL`            | `redis://localhost:6379`                              | String de conexão do Redis                       | Usar Redis autenticado/compartilhado com isolamento por ambiente                    |
| `JWT_SECRET`           | `auronix`                                             | Segredo de assinatura do JWT                     | Precisa ser sobrescrito                                                             |
| `COOKIE_SECRET`        | `auronix`                                             | Segredo de cookie do Fastify                     | Precisa ser sobrescrito                                                             |
| `ARGON2_PEPPER`        | `auronix`                                             | Pepper secreto usado no hash de senha            | Precisa ser sobrescrito e armazenado com segurança                                  |
| `CLIENT_URLS`          | `http://localhost:4200`                               | Origens permitidas separadas por ponto e vírgula | Manter estrito e específico por ambiente                                            |

### Passo a passo local

1. Instale as dependências.

```bash
yarn install
```

2. Inicie PostgreSQL e Redis, localmente ou via Docker.

```bash
docker compose up -d db cache
```

3. Configure as variáveis de ambiente. O `ConfigModule` do Nest lê o ambiente do processo e também pode consumir um arquivo local `.env`.

<details>
<summary>Exemplo de <code>.env</code></summary>

```dotenv
PORT=3000
NODE_ENV=development
POSTGRES_URL=postgres://postgres:postgres@localhost:5432/auronix
POSTGRES_SYNCHRONIZE=true
REDIS_URL=redis://localhost:6379
JWT_SECRET=change-me
COOKIE_SECRET=change-me
ARGON2_PEPPER=change-me
CLIENT_URLS=http://localhost:4200
```

</details>

4. Inicie a aplicação em watch mode.

```bash
yarn start:dev
```

### Observações sobre Docker

- `compose.yaml` provisiona API, PostgreSQL e Redis.
- O compose inicia o servidor com `NODE_ENV=development` e URLs internas de serviço.
- O setup versionado em compose depende dos segredos default da config da aplicação caso você não os sobrescreva externamente.

<a id="running-the-project"></a>

## Execução do Projeto

| Comando                     | Caso de uso                     | Observações                                           |
| --------------------------- | ------------------------------- | ----------------------------------------------------- |
| `yarn start:dev`            | Desenvolvimento local           | Watch mode do Nest                                    |
| `yarn start:debug`          | Depuração local                 | Inicia o Nest em modo debug + watch                   |
| `yarn build`                | Geração do artefato de produção | Escreve os arquivos compilados em `dist/`             |
| `yarn start:prod`           | Executar a aplicação compilada  | Espera `dist/main.js` e variáveis seguras de ambiente |
| `docker compose up --build` | Stack completa em container     | Faz build da imagem e sobe API, PostgreSQL e Redis    |

### Fluxo local típico

```bash
docker compose up -d db cache
yarn start:dev
```

### Fluxo típico em estilo produção

```bash
yarn build
# garanta que as variáveis de ambiente de produção estejam definidas,
# especialmente NODE_ENV=production e POSTGRES_SYNCHRONIZE=false
yarn start:prod
```

<a id="testing-strategy"></a>

## Estratégia de Testes

O repositório possui specs mais unitárias em `src/**/__test__` e cobertura end-to-end em `test/e2e`. A suíte e2e exercita integrações reais com PostgreSQL e Redis, incluindo concorrência em transferências, liquidação assíncrona e replay de SSE.

| Camada de teste                        | Ferramentas                               | O que cobre                                                                                         |
| -------------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Specs unitárias / service / controller | Jest + utilitários de teste do Nest       | Validação da lógica de serviço, delegação de controllers, auth guard, adapters de fila e processors |
| Testes end-to-end                      | Jest + Supertest + PostgreSQL/Redis reais | Fluxos HTTP públicos, processamento assíncrono, condições de corrida e streaming de notificações    |

| Comando verificado      | Resultado observado                      |
| ----------------------- | ---------------------------------------- |
| `yarn test --runInBand` | `13/13` suites, `59/59` testes aprovados |
| `yarn test:e2e`         | `3/3` suites, `40/40` testes aprovados   |

<details>
<summary>Detalhes do ambiente e2e</summary>

- O helper de e2e configura `NODE_ENV=test`.
- A URL default do PostgreSQL para e2e é `postgres://postgres:postgres@localhost:5432/auronix_e2e`.
- A URL default do Redis para e2e é `redis://localhost:6379/1`.
- O harness de testes cria o banco e2e automaticamente quando possível, trunca tabelas entre testes, limpa o Redis e consegue pausar/retomar a fila de transferências para simular corridas de forma determinística.

</details>

<a id="api-reference"></a>

## Referência de API

Todos os endpoints autenticados dependem do cookie `auth_cookie`, emitido por `POST /user`, `POST /user/login` ou renovado por `GET /user`. Mensagens de validação e de exceção atualmente são emitidas em português porque as strings da aplicação estão hard-coded dessa forma.

| Método   | Caminho                | Auth | Descrição                                       | Observações                                      |
| -------- | ---------------------- | ---- | ----------------------------------------------- | ------------------------------------------------ |
| `GET`    | `/health`              | Não  | Probe de liveness                               | Retorna texto plano `ok`                         |
| `POST`   | `/user`                | Não  | Registra um novo usuário                        | Define `auth_cookie` e retorna o usuário criado  |
| `POST`   | `/user/login`          | Não  | Autentica um usuário existente                  | Define `auth_cookie` e retorna o usuário         |
| `POST`   | `/user/logout`         | Não  | Limpa o cookie de autenticação                  | Não exige auth atual                             |
| `GET`    | `/user`                | Sim  | Retorna o usuário atual e emite cookie renovado | Comportamento de refresh de sessão               |
| `GET`    | `/user/:email`         | Sim  | Busca um usuário por email                      | Autenticado, mas não escopado ao próprio usuário |
| `PATCH`  | `/user`                | Sim  | Atualiza o usuário atual                        | Aceita atualização parcial                       |
| `DELETE` | `/user`                | Sim  | Remove o usuário atual                          | Exclusão direta; sem soft-delete                 |
| `POST`   | `/payment-request`     | Sim  | Cria uma cobrança de curta duração              | Expira em 10 minutos                             |
| `GET`    | `/payment-request/:id` | Sim  | Recupera uma cobrança ainda válida              | Autenticado, sem ownership restriction           |
| `POST`   | `/transfer`            | Sim  | Cria uma transferência pendente                 | A liquidação final é assíncrona                  |
| `GET`    | `/transfer/:id`        | Sim  | Recupera uma transferência                      | Restrito a pagador ou destinatário               |
| `GET`    | `/transfer`            | Sim  | Lista transferências concluídas                 | Paginação por cursor                             |
| `GET`    | `/notification/stream` | Sim  | Abre stream SSE de notificações                 | Suporta replay com `Last-Event-ID`               |

<details>
<summary>Exemplo de cadastro e login de usuário</summary>

```bash
curl -i http://localhost:3000/user \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@auronix.test",
    "name": "John Doe",
    "password": "Password@123"
  }'
```

```json
{
  "id": "7c91bf96-4a7f-4c8f-9aa3-b8382b6fd4c4",
  "email": "john@auronix.test",
  "name": "John Doe",
  "balance": 100000,
  "createdAt": "2026-04-05T12:00:00.000Z",
  "updatedAt": "2026-04-05T12:00:00.000Z"
}
```

```bash
curl -i http://localhost:3000/user/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@auronix.test",
    "password": "Password@123"
  }'
```

Comportamento esperado:

- A resposta define `Set-Cookie: auth_cookie=...; HttpOnly; Secure; SameSite=Strict; Path=/`
- Chamadas autenticadas subsequentes enviam `Cookie: auth_cookie=...`

</details>

<details>
<summary>Exemplo de criação de transferência</summary>

```bash
curl -X POST http://localhost:3000/transfer \
  -H "Content-Type: application/json" \
  -H "Cookie: auth_cookie=<token>" \
  -d '{
    "payeeId": "2f2da9e7-65e0-45ca-8db2-a43d8438aa93",
    "value": 5000,
    "description": "Liquidação principal"
  }'
```

```json
{
  "id": "4a6d74a4-6bc8-4026-befd-277603fd93d5",
  "value": 5000,
  "description": "Liquidação principal",
  "status": "pending",
  "failureReason": null,
  "completedAt": null,
  "payer": {
    "id": "7c91bf96-4a7f-4c8f-9aa3-b8382b6fd4c4",
    "email": "john@auronix.test",
    "name": "John Doe"
  },
  "payee": {
    "id": "2f2da9e7-65e0-45ca-8db2-a43d8438aa93",
    "email": "jane@auronix.test",
    "name": "Jane Doe"
  },
  "createdAt": "2026-04-05T12:01:00.000Z",
  "updatedAt": "2026-04-05T12:01:00.000Z"
}
```

O cliente precisa fazer polling em `GET /transfer/:id` ou consumir `/notification/stream` para observar o estado terminal `completed` ou `failed`.

</details>

<details>
<summary>Exemplo de histórico paginado de transferências</summary>

```bash
curl --get http://localhost:3000/transfer \
  -H "Cookie: auth_cookie=<token>" \
  --data-urlencode "limit=1"
```

```json
{
  "data": [
    {
      "id": "9c3871d4-6ba1-4a9f-bbc0-b0870f54f03f",
      "value": 4000,
      "description": "Segunda transferência",
      "status": "completed",
      "completedAt": "2026-04-05T12:05:00.000Z",
      "payer": {
        "id": "7c91bf96-4a7f-4c8f-9aa3-b8382b6fd4c4",
        "email": "john@auronix.test",
        "name": "John Doe"
      },
      "payee": {
        "id": "2f2da9e7-65e0-45ca-8db2-a43d8438aa93",
        "email": "jane@auronix.test",
        "name": "Jane Doe"
      }
    }
  ],
  "next": {
    "completedAt": "2026-04-05T12:05:00.000Z",
    "id": "9c3871d4-6ba1-4a9f-bbc0-b0870f54f03f"
  }
}
```

```bash
curl --get http://localhost:3000/transfer \
  -H "Cookie: auth_cookie=<token>" \
  --data-urlencode "limit=1" \
  --data-urlencode 'cursor={"completedAt":"2026-04-05T12:05:00.000Z","id":"9c3871d4-6ba1-4a9f-bbc0-b0870f54f03f"}'
```

</details>

<details>
<summary>Exemplo de cobrança</summary>

```bash
curl -X POST http://localhost:3000/payment-request \
  -H "Content-Type: application/json" \
  -H "Cookie: auth_cookie=<token>" \
  -d '{
    "value": 5000
  }'
```

```json
{
  "id": "0b32a31c-9b37-435a-8d68-43a1bb6ba18c",
  "value": 5000,
  "createdAt": "2026-04-05T12:10:00.000Z"
}
```

```bash
curl http://localhost:3000/payment-request/0b32a31c-9b37-435a-8d68-43a1bb6ba18c \
  -H "Cookie: auth_cookie=<token>"
```

Cobranças expiradas retornam `404` mesmo que o worker atrasado de limpeza ainda não tenha removido a linha.

</details>

<details>
<summary>Exemplo de notificação SSE e replay</summary>

```bash
curl -N http://localhost:3000/notification/stream \
  -H "Cookie: auth_cookie=<token>"
```

Exemplo de frame de evento:

```text
id: 42
event: transfer.completed
data: {"type":"transfer.completed","data":{"transferId":"4a6d74a4-6bc8-4026-befd-277603fd93d5","amount":5000,"createdAt":"2026-04-05T12:01:00.000Z","description":"Liquidação principal","balance":95000,"payer":{"id":"7c91bf96-4a7f-4c8f-9aa3-b8382b6fd4c4","email":"john@auronix.test","name":"John Doe"}}}
```

Requisição de replay:

```bash
curl -N http://localhost:3000/notification/stream \
  -H "Cookie: auth_cookie=<token>" \
  -H "Last-Event-ID: 41"
```

Comportamento de replay:

- Apenas eventos com id maior que `Last-Event-ID` são reenviados.
- O histórico de replay é limitado aos últimos `100` eventos por usuário e expira após `24` horas no Redis.
- Os payloads atuais de transferência contêm metadados da transferência, saldo resultante e um snapshot do pagador; os eventos ainda não são versionados como contrato público formal.

</details>
