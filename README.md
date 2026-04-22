# Auronix Server

[Português (Brasil)](README.pt-BR.md)

<a id="table-of-contents"></a>

## Table of Contents

- [Project Overview](#project-overview)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Domain & Core Concepts](#domain-core-concepts)
- [Implementation Details](#implementation-details)
- [Engineering Decisions & Trade-offs](#engineering-decisions-trade-offs)
- [Performance Considerations](#performance-considerations)
- [Security Considerations](#security-considerations)
- [Scalability & Reliability](#scalability-reliability)
- [Development Setup](#development-setup)
- [Running the Project](#running-the-project)
- [Testing Strategy](#testing-strategy)
- [Observability](#observability)
- [API Reference](#api-reference)
- [Roadmap / Future Improvements](#roadmap-future-improvements)

<a id="project-overview"></a>

## Project Overview

Auronix Server is a NestJS modular monolith that exposes an HTTP API for account management, internal stored-value transfers, expiring payment requests, and real-time notification delivery over Server-Sent Events (SSE). PostgreSQL is the system of record for durable business data. Redis backs BullMQ queues, notification replay buffers, and cross-instance pub/sub.

The core value proposition is predictable transactional behavior for wallet-like balance movement without introducing microservice coordination overhead. Transfers are accepted synchronously, settled asynchronously, and protected against double spend through database-level locking in the worker path.

<a id="system-architecture"></a>

## System Architecture

The application is organized as a modular monolith. HTTP controllers, domain services, queue processors, and infrastructure adapters are packaged into NestJS modules and deployed as one runtime. That favors transactional simplicity and implementation speed over service isolation.

API and worker responsibilities are currently co-located in the same process. Horizontal scale is therefore possible, but it scales combined web and worker capacity together rather than independently.

```mermaid
flowchart TD
  Client[Browser / API Client]
  SSE[SSE Consumers]

  subgraph App["Auronix Server (NestJS modular monolith)"]
    Fastify[Fastify HTTP adapter]

    subgraph Core["Core modules"]
      UserModule[User module]
      TransferModule[Transfer module]
      PaymentRequestModule[Payment Request module]
    end

    subgraph Shared["Shared modules"]
      AuthModule[Auth module]
      DatabaseModule[Database module]
      CacheModule[Redis cache module]
      NotificationModule[Notification module]
      Workers[BullMQ processors]
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

| Component              | Responsibility                                                                      | Notes                                                                 |
| ---------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `UserModule`           | Registration, login, session refresh, profile updates, deletion                     | Sets and clears `auth_cookie` on successful auth flows                |
| `TransferModule`       | Transfer creation, transfer retrieval, paginated transfer history, async settlement | Uses BullMQ and pessimistic row locks to preserve balance correctness |
| `PaymentRequestModule` | Payment request creation and delayed expiration cleanup                             | Expired requests become unreadable before the cleanup job runs        |
| `NotificationModule`   | SSE stream handling, replay lookup, Redis pub/sub fan-out                           | Keeps per-user in-memory connection registries per process            |
| `DatabaseModule`       | Transaction abstraction over TypeORM/PostgreSQL                                     | Transfer settlement is wrapped in a single database transaction       |
| `CacheModule`          | Redis access for queues, replay buffers, counters, pub/sub                          | Redis is not used as a general read cache for business queries        |

### Transfer Processing Sequence

```mermaid
sequenceDiagram
  participant Client
  participant API as TransferController/Service
  participant DB as PostgreSQL
  participant Q as BullMQ Transfer Queue
  participant Worker as TransferProcessor
  participant NQ as Notification Queue
  participant Redis as Redis
  participant SSE as Notification Gateway

  Client->>API: POST /transfer
  API->>DB: Transaction: validate payer/payee, check balance, insert pending transfer
  DB-->>API: pending transfer
  API->>Q: enqueue process-transfer job
  API-->>Client: 201 Created (status=pending)

  Q->>Worker: dequeue transfer job
  Worker->>DB: Transaction: lock payer/payee rows, re-check balance
  Worker->>DB: update balances and mark transfer completed
  Worker->>NQ: enqueue notification jobs
  NQ->>Redis: publish notification payloads + append replay list
  Redis-->>SSE: pub/sub event
  SSE-->>Client: transfer.completed / transfer.failed
```

| Architectural style | Current choice                         | Reason                                                                                                     |
| ------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Deployment unit     | Single NestJS service                  | Keeps consistency boundaries local and removes inter-service coordination for the core money movement flow |
| Asynchronous work   | BullMQ workers inside the application  | Offloads transfer settlement and expiration cleanup without adding another service codebase                |
| Real-time delivery  | SSE over authenticated cookie sessions | Fits one-way event streaming with simpler browser integration than WebSockets                              |

<a id="tech-stack"></a>

## Tech Stack

| Technology                          | Version / family                        | Where it is used                                       | Why this choice                                                                                     |
| ----------------------------------- | --------------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| Node.js                             | `24.x`                                  | Runtime and container base image                       | Current runtime baseline in the repository; modern language features and long-lived async workloads |
| TypeScript                          | `5.7.x`                                 | Entire codebase                                        | Strong typing for service boundaries, DTOs, repositories, and test helpers                          |
| NestJS                              | `11.x`                                  | Application framework                                  | Module system, DI, testing support, BullMQ integration, and consistent controller/service structure |
| Fastify adapter                     | Nest Fastify platform                   | HTTP server                                            | Leaner request overhead than Express and sufficient ecosystem support for this service              |
| TypeORM                             | `0.3.x`                                 | Entities, repositories, transactions                   | Tight Nest integration and pragmatic entity/repository support for a small modular monolith         |
| PostgreSQL                          | `16` in compose                         | System of record                                       | Transactional integrity, row locking, indexes, and durable state for user balances and transfers    |
| Redis                               | `7` in compose                          | BullMQ backend, notification replay, pub/sub, counters | Shared low-latency infrastructure for queues and event fan-out                                      |
| BullMQ                              | `5.x`                                   | Transfer, notification, payment request workers        | Retries, delayed jobs, concurrency control, and Redis-native operational model                      |
| JWT                                 | Nest JWT module                         | Session token generation and verification              | Compact stateless session payload stored in secure HTTP-only cookies                                |
| Argon2id                            | `argon2` package                        | Password hashing                                       | Modern password hashing algorithm with configurable memory/time costs and pepper support            |
| class-validator / class-transformer | Current Nest-compatible versions        | DTO validation and query transformation                | Rejects malformed payloads and non-whitelisted fields at the framework boundary                     |
| Jest + Supertest                    | Current repo versions                   | Unit and e2e tests                                     | Standard Nest testing toolchain with adequate HTTP-level coverage                                   |
| Docker / Compose                    | Multi-stage Dockerfile + `compose.yaml` | Local container orchestration                          | Reproducible local runtime for app, PostgreSQL, and Redis                                           |

<a id="domain-core-concepts"></a>

## Domain & Core Concepts

All monetary values are stored as integer cents, not floating-point decimal values. This is the baseline invariant behind balances, transfers, and payment requests.

| Entity / concept   | Purpose                                   | Important fields                                                    |
| ------------------ | ----------------------------------------- | ------------------------------------------------------------------- |
| `User`             | Account owner and balance holder          | `id`, `email`, `name`, `password`, `balance`, timestamps            |
| `Transfer`         | Ledger movement between two users         | `payer`, `payee`, `value`, `status`, `failureReason`, `completedAt` |
| `PaymentRequest`   | Short-lived request for a specific amount | `id`, `value`, `user`, `expiresAt`, `createdAt`                     |
| Notification event | Real-time transfer outcome signal         | `id`, `userId`, `type`, `data`, `occurredAt`                        |
| Transfer status    | Transfer lifecycle state                  | `pending`, `completed`, `failed`                                    |

### Current invariants

- Each new user starts with `100000` cents of balance.
- Transfers are created as `pending` and settled asynchronously by a BullMQ worker.
- Self-transfers are rejected before persistence.
- Balance sufficiency is checked twice: once before the transfer record is created and again during worker settlement under row locks.
- Transfer completion and transfer failure produce notification events for SSE delivery.
- Payment requests expire after 10 minutes.
- `GET /payment-request/:id` returns `404` once a request is expired, even before the delayed deletion job removes the row.
- Transfer history pagination is cursor-based and ordered by `(completedAt DESC, id DESC)`.
- `GET /transfer` returns completed transfers only; pending and failed transfers are available by identifier, not through the history listing.

```mermaid
stateDiagram-v2
  [*] --> Pending
  Pending --> Completed: worker settlement succeeds
  Pending --> Failed: queue enqueue failure / unrecoverable processing error / retries exhausted
  Completed --> [*]
  Failed --> [*]
```

<a id="implementation-details"></a>

## Implementation Details

### Folder structure

| Path                   | Role                                                                  |
| ---------------------- | --------------------------------------------------------------------- |
| `src/main.ts`          | Fastify bootstrap, Helmet, CORS, validation pipe, cookie registration |
| `src/app.module.ts`    | Top-level module composition, TypeORM, BullMQ, throttling             |
| `src/core/*`           | Business-facing modules: user, transfer, payment request              |
| `src/shared/modules/*` | Cross-cutting infrastructure: auth, cache, database, notifications    |
| `src/config/*`         | Environment-derived configuration loaders and types                   |
| `test/e2e/*`           | End-to-end scenarios using live PostgreSQL and Redis                  |

<details>
<summary>Repository tree excerpt</summary>

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

### Module and code patterns

| Pattern                                           | Where it appears                                                      | Why it matters                                                             |
| ------------------------------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Thin controllers                                  | Core and notification controllers                                     | Validation and transport stay at the edge; orchestration stays in services |
| Repository abstraction via DI tokens              | `IUserRepository`, `ITransferRepository`, `IPaymentRequestRepository` | Keeps service code decoupled from TypeORM-specific implementations         |
| Explicit transaction wrapper                      | `IDatabaseService` and `DatabaseTransaction`                          | Makes transaction boundaries visible in transfer creation and settlement   |
| Queue adapter + processor split                   | `*.queue.ts` and `*.processor.ts`                                     | Separates enqueue intent from background execution                         |
| In-memory SSE connection registry + Redis pub/sub | `NotificationGateway` and `NotificationService`                       | Supports multi-instance fan-out without sticky sessions                    |
| DTO transformation for cursor pagination          | `FindTransferDto`                                                     | Accepts JSON-encoded cursor input and validates it before repository use   |

### Request lifecycle

1. A request reaches Fastify and passes global validation, cookie parsing, and optional authentication.
2. The controller delegates to a service, which coordinates repositories, transactions, and queue adapters.
3. Durable state is stored in PostgreSQL. Deferred work is pushed to BullMQ through Redis.
4. Worker processors complete background tasks and emit notifications, which are replayable for reconnecting SSE clients.

### Transfer lifecycle in practice

| Phase         | Behavior                                                                                                                  |
| ------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Creation      | Validates payer/payee, rejects self-transfer, checks balance, inserts `pending` transfer inside a database transaction    |
| Queue handoff | Enqueues a deterministic BullMQ job using the transfer id as `jobId`                                                      |
| Settlement    | Worker locks both user rows with pessimistic write locks, re-checks balance, updates balances, marks transfer `completed` |
| Failure path  | Queue failures or unrecoverable processing errors mark the transfer as `failed` and preserve a failure reason             |
| Notification  | Transfer outcomes are queued again, serialized, replay-buffered in Redis, then emitted to connected SSE clients           |

<a id="engineering-decisions-trade-offs"></a>

## Engineering Decisions & Trade-offs

| Decision area         | Adopted approach                          | Benefit                                                     | Trade-off                                                                    |
| --------------------- | ----------------------------------------- | ----------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Service decomposition | Modular monolith instead of microservices | Keeps balance transfer consistency and deployment simple    | Fewer independent scaling and isolation boundaries                           |
| Transfer execution    | Asynchronous settlement via BullMQ        | Absorbs transient worker failures and keeps API latency low | Final transfer status is not known at `POST /transfer` response time         |
| Concurrency control   | Pessimistic row locking during settlement | Strong protection against double spend under concurrency    | Higher write contention under heavy transfer volume                          |
| History pagination    | Cursor pagination on `(completedAt, id)`  | Stable ordering and pagination under inserts                | Slightly more complex client state than offset pagination                    |
| Schema management     | TypeORM `synchronize`                     | Fast local iteration and minimal bootstrapping              | Unsafe for disciplined production schema evolution; no audited migrations    |
| Notification replay   | Redis list with TTL and bounded size      | Simple replay for reconnecting SSE clients                  | Replay durability is limited to Redis retention, not permanent event storage |
| Deployment topology   | API and workers in the same process       | Small operational footprint                                 | No clean separation between web-only and worker-only scaling                 |

<details>
<summary>Alternatives explicitly not taken</summary>

| Alternative                                                | Why it was not adopted in the current codebase                                                              |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Microservices for user, transfer, and notification domains | Would require distributed coordination around balance mutation and significantly more operational machinery |
| Offset-based transfer history pagination                   | Degrades under concurrent inserts and becomes unstable for chronological financial history                  |
| Optimistic concurrency with version retries                | Adds retry complexity where strict row locking already solves the current correctness problem               |
| Persistent event store for notifications                   | More durable, but materially more complex than the current replay requirement                               |
| Dedicated migration system as the only schema path         | Preferable for production, but not how the current repository is wired today                                |

</details>

<a id="performance-considerations"></a>

## Performance Considerations

| Concern                                  | Current mitigation                                                                                                 | Practical impact                                                             |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Monetary arithmetic correctness          | Integer cents only                                                                                                 | Avoids floating-point drift and reconciliation errors                        |
| Transfer race conditions                 | Pessimistic write lock on both user rows during worker settlement                                                  | Prevents double spend in concurrent transfer scenarios                       |
| Transfer request latency                 | Async settlement through BullMQ                                                                                    | `POST /transfer` stays bounded to validation, persistence, and queue handoff |
| History pagination cost                  | Cursor pagination with composite ordering                                                                          | Better page stability and predictable query shape than offsets               |
| Read performance for lookup-heavy fields | Unique/indexed `users.email`, indexed `payment_requests.expires_at`, indexed transfer payer/payee/completed fields | Keeps primary read paths bounded                                             |
| Queue throughput                         | Processor concurrency set to `10` for transfer/payment request and `20` for notifications                          | Acceptable baseline without overcommitting worker parallelism                |
| SSE reconnect overhead                   | Replay limited to the latest `100` events per user with `24h` TTL                                                  | Bounds Redis memory usage while supporting short disconnect recovery         |
| Cache invalidation complexity            | No general-purpose business read cache                                                                             | Simpler consistency model at the cost of more direct PostgreSQL reads        |

### Known bottlenecks

- The database remains the primary scaling boundary because transfer settlement is correctness-sensitive and lock-based.
- API and worker roles share the same runtime, so CPU-intensive or queue-heavy workloads can compete with HTTP handling.
- Notification replay is Redis-memory bound and intentionally not durable beyond the configured TTL and list size.

<a id="security-considerations"></a>

## Security Considerations

| Area             | Current implementation                                                               | Gap or operational note                                                              |
| ---------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| Authentication   | JWT signed by Nest JWT, stored in `auth_cookie`                                      | Cookie auth is simple for browser clients but requires disciplined secret management |
| Password storage | Argon2id with pepper, `memoryCost=65536`, `timeCost=3`, `hashLength=32`              | Pepper defaults exist in config and must be overridden outside local development     |
| HTTP hardening   | Helmet with CSP, HSTS, frameguard, no-sniff, hide-powered-by                         | Strong baseline headers, but CSP still allows inline styles                          |
| Input validation | Global `ValidationPipe` with `whitelist`, `forbidNonWhitelisted`, and transformation | Good boundary protection; error payloads are currently returned in Portuguese        |
| Rate limiting    | Global Nest throttler, `10` requests per `60s`                                       | Single global policy only; no route-specific tuning                                  |
| CORS             | Allowlist comes from `CLIENT_URLS`; credentials enabled                              | Cross-site cookie flows require correct origin configuration                         |
| Cookie settings  | `HttpOnly`, `Secure`, `SameSite=Strict`, path `/`                                    | Browser-based non-HTTPS environments may need trusted localhost or TLS termination   |
| Authorization    | Transfers are resource-scoped to payer/payee                                         | No RBAC model; some authenticated reads are broader than transfer reads              |
| CSRF             | No explicit CSRF token or same-site anti-CSRF flow                                   | Important gap for cookie-based browser sessions                                      |
| Secrets          | `JWT_SECRET`, `COOKIE_SECRET`, `ARGON2_PEPPER` fall back to hard-coded defaults      | Must be overridden in every non-local environment                                    |

### Authorization boundaries worth calling out

- `GET /transfer/:id` is restricted to the payer or payee of that transfer.
- `GET /user/:email` requires authentication, but is not scoped to the caller's own identity.
- `GET /payment-request/:id` requires authentication, but is identifier-based and not ownership-restricted.

<a id="scalability-reliability"></a>

## Scalability & Reliability

| Topic                               | Current posture                                                                      | Limitation                                                                      |
| ----------------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| Horizontal scaling                  | Multiple application instances can share PostgreSQL and Redis                        | Web and worker capacity scale together because roles are not split              |
| SSE fan-out                         | Redis pub/sub lets each instance emit to its own local connections                   | Replay is bounded and not durable beyond Redis retention                        |
| Queue resilience                    | BullMQ default job attempts set to `5` with exponential backoff starting at `1000ms` | No dead-letter queue or separate failure topic                                  |
| Transfer idempotency at queue layer | Transfer jobs use `transferId` as `jobId`                                            | Does not replace end-to-end idempotency keys at the API boundary                |
| Notification enqueue deduplication  | Notification job id is derived from a SHA-256 hash of payload                        | Payload-level dedupe can suppress identical repeated notifications by design    |
| Payment request cleanup             | Delayed expiration job plus query-time expiry filter                                 | Cleanup timing is best effort; visibility rule is stronger than deletion timing |
| Health reporting                    | `/health` returns a simple liveness response                                         | No dependency-aware readiness or degraded-mode signal                           |
| Data consistency                    | Transfer settlement is transactional inside PostgreSQL                               | The system still depends on a single primary database for correctness           |

### Reliability behaviors already present

- Transfer processing failures that become final are converted into persisted `failed` transfer states.
- Notification publication failures are logged and partially tolerated rather than crashing the request path.
- Expired payment requests are hidden even if the delayed cleanup worker has not executed yet.
- SSE connections send heartbeats every 25 seconds and support `Last-Event-ID` replay.

<a id="development-setup"></a>

## Development Setup

### Prerequisites

| Dependency                     | Recommended version | Purpose                                 |
| ------------------------------ | ------------------- | --------------------------------------- |
| Node.js                        | `24.x`              | Runtime for local development and build |
| Yarn                           | Classic `1.x`       | Package manager used by the repository  |
| PostgreSQL                     | `16+`               | Primary datastore                       |
| Redis                          | `7+`                | Queue backend, pub/sub, replay store    |
| Docker Desktop / Docker Engine | Current             | Optional containerized setup            |

### Environment variables

| Variable               | Default in code                                       | Purpose                                    | Production note                                                    |
| ---------------------- | ----------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------ |
| `PORT`                 | `3000`                                                | HTTP listen port                           | Usually injected by the runtime platform                           |
| `NODE_ENV`             | `development`                                         | App environment mode                       | Set explicitly to `production` outside local dev                   |
| `POSTGRES_URL`         | `postgres://postgres:postgres@localhost:5432/auronix` | PostgreSQL connection string               | Point to managed PostgreSQL and use TLS/network controls as needed |
| `POSTGRES_SYNCHRONIZE` | `true`                                                | TypeORM schema synchronization toggle      | Set to `false` in production and replace with real migrations      |
| `REDIS_URL`            | `redis://localhost:6379`                              | Redis connection string                    | Use authenticated/shared Redis with environment-specific isolation |
| `JWT_SECRET`           | `auronix`                                             | JWT signing secret                         | Must be overridden                                                 |
| `COOKIE_SECRET`        | `auronix`                                             | Fastify cookie secret                      | Must be overridden                                                 |
| `ARGON2_PEPPER`        | `auronix`                                             | Secret pepper used during password hashing | Must be overridden and stored securely                             |
| `CLIENT_URLS`          | `http://localhost:4200`                               | Semicolon-delimited allowed origins        | Keep strict and environment-specific                               |

### Local setup steps

1. Install dependencies.

```bash
yarn install
```

2. Start PostgreSQL and Redis, either locally or via Docker.

```bash
docker compose up -d db cache
```

3. Configure environment variables. Nest `ConfigModule` will read from process environment and can also consume a local `.env` file.

<details>
<summary>Example <code>.env</code></summary>

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

4. Start the application in watch mode.

```bash
yarn start:dev
```

### Docker notes

- `compose.yaml` provisions the API, PostgreSQL, and Redis.
- The compose file starts the server with `NODE_ENV=development` and container-local service URLs.
- The checked-in compose setup relies on default secrets from application config unless you override them externally.

<a id="running-the-project"></a>

## Running the Project

| Command                     | Use case                             | Notes                                                      |
| --------------------------- | ------------------------------------ | ---------------------------------------------------------- |
| `yarn start:dev`            | Local development                    | Nest watch mode                                            |
| `yarn start:debug`          | Local debugging                      | Starts Nest in debug + watch mode                          |
| `yarn build`                | Production build artifact generation | Outputs compiled files to `dist/`                          |
| `yarn start:prod`           | Run compiled application             | Expects `dist/main.js` and production-safe env vars        |
| `docker compose up --build` | Full containerized stack             | Builds the app image and starts API, PostgreSQL, and Redis |

### Typical local workflow

```bash
docker compose up -d db cache
yarn start:dev
```

### Typical production-style workflow

```bash
yarn build
# ensure production environment variables are set,
# especially NODE_ENV=production and POSTGRES_SYNCHRONIZE=false
yarn start:prod
```

<a id="testing-strategy"></a>

## Testing Strategy

The repository has both unit-oriented specs under `src/**/__test__` and end-to-end coverage under `test/e2e`. The e2e suite exercises real PostgreSQL and Redis integrations, including transfer concurrency, asynchronous settlement, and SSE replay behavior.

| Test layer                        | Tooling                                  | What it covers                                                                                          |
| --------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Unit / service / controller specs | Jest + Nest testing utilities            | Validation of service logic, controller delegation, auth guard behavior, queue adapters, and processors |
| End-to-end tests                  | Jest + Supertest + live PostgreSQL/Redis | Public HTTP flows, async queue processing, race conditions, and notification streaming                  |

| Verified command        | Observed result                      |
| ----------------------- | ------------------------------------ |
| `yarn test --runInBand` | `13/13` suites, `59/59` tests passed |
| `yarn test:e2e`         | `3/3` suites, `40/40` tests passed   |

<details>
<summary>E2E environment details</summary>

- The e2e helper configures `NODE_ENV=test`.
- Default e2e PostgreSQL URL is `postgres://postgres:postgres@localhost:5432/auronix_e2e`.
- Default e2e Redis URL is `redis://localhost:6379/1`.
- The test harness auto-creates the e2e database when possible, truncates tables between tests, flushes Redis, and can pause/resume the transfer queue to simulate race conditions deterministically.

</details>

<a id="api-reference"></a>

## API Reference

All authenticated endpoints rely on the `auth_cookie` cookie issued by `POST /user`, `POST /user/login`, or refreshed by `GET /user`. Validation and exception messages are currently emitted in Portuguese because the application strings are hard-coded that way.

| Method   | Path                   | Auth | Description                                          | Notes                                           |
| -------- | ---------------------- | ---- | ---------------------------------------------------- | ----------------------------------------------- |
| `GET`    | `/health`              | No   | Liveness probe                                       | Returns plain text `ok`                         |
| `POST`   | `/user`                | No   | Register a new user                                  | Sets `auth_cookie` and returns the created user |
| `POST`   | `/user/login`          | No   | Authenticate an existing user                        | Sets `auth_cookie` and returns the user         |
| `POST`   | `/user/logout`         | No   | Clear auth cookie                                    | Does not require current auth                   |
| `GET`    | `/user`                | Yes  | Return the current user and issue a refreshed cookie | Session refresh behavior                        |
| `GET`    | `/user/:email`         | Yes  | Look up a user by email                              | Authenticated, but not self-scoped              |
| `PATCH`  | `/user`                | Yes  | Update current user                                  | Accepts partial update fields                   |
| `DELETE` | `/user`                | Yes  | Delete current user                                  | Direct delete; no soft-delete                   |
| `POST`   | `/payment-request`     | Yes  | Create a short-lived payment request                 | Expires after 10 minutes                        |
| `GET`    | `/payment-request/:id` | Yes  | Retrieve an unexpired payment request                | Authenticated, not ownership-scoped             |
| `POST`   | `/transfer`            | Yes  | Create a pending transfer                            | Final settlement is asynchronous                |
| `GET`    | `/transfer/:id`        | Yes  | Retrieve a transfer                                  | Restricted to payer or payee                    |
| `GET`    | `/transfer`            | Yes  | List completed transfers                             | Cursor-based pagination                         |
| `GET`    | `/notification/stream` | Yes  | Open SSE stream for notifications                    | Supports `Last-Event-ID` replay                 |

<details>
<summary>User registration and login example</summary>

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

Expected behavior:

- Response sets `Set-Cookie: auth_cookie=...; HttpOnly; Secure; SameSite=Strict; Path=/`
- Subsequent authenticated calls send `Cookie: auth_cookie=...`

</details>

<details>
<summary>Transfer creation example</summary>

```bash
curl -X POST http://localhost:3000/transfer \
  -H "Content-Type: application/json" \
  -H "Cookie: auth_cookie=<token>" \
  -d '{
    "payeeId": "2f2da9e7-65e0-45ca-8db2-a43d8438aa93",
    "value": 5000,
    "description": "Primary settlement"
  }'
```

```json
{
  "id": "4a6d74a4-6bc8-4026-befd-277603fd93d5",
  "value": 5000,
  "description": "Primary settlement",
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

The client must poll `GET /transfer/:id` or consume `/notification/stream` to observe the terminal `completed` or `failed` state.

</details>

<details>
<summary>Paginated transfer history example</summary>

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
      "description": "Second transfer",
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
<summary>Payment request example</summary>

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

Expired requests return `404` even if the delayed cleanup worker has not yet removed the row.

</details>

<details>
<summary>SSE notification and replay example</summary>

```bash
curl -N http://localhost:3000/notification/stream \
  -H "Cookie: auth_cookie=<token>"
```

Example event frame:

```text
id: 42
event: transfer.completed
data: {"type":"transfer.completed","data":{"transferId":"4a6d74a4-6bc8-4026-befd-277603fd93d5","amount":5000,"createdAt":"2026-04-05T12:01:00.000Z","description":"Primary settlement","balance":95000,"payer":{"id":"7c91bf96-4a7f-4c8f-9aa3-b8382b6fd4c4","email":"john@auronix.test","name":"John Doe"}}}
```

Replay request:

```bash
curl -N http://localhost:3000/notification/stream \
  -H "Cookie: auth_cookie=<token>" \
  -H "Last-Event-ID: 41"
```

Replay behavior:

- Only events with ids greater than `Last-Event-ID` are replayed.
- Replay history is limited to the latest `100` events per user and expires after `24` hours in Redis.
- Current transfer payloads contain transfer metadata, resulting balance, and a payer snapshot; event payloads are not yet versioned as a formal public contract.

</details>
