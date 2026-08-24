# Application

## Project Structure

Application code lives under `src/main/java/dev/joaopdias/auronix`.

- `config`: Spring Security, CORS, RabbitMQ topology, and Redis Pub/Sub configuration.
- `core.user`: registration, login, token refresh, profile update, deletion, DTOs, entity, and repository.
- `core.account`: account creation and lookup, JPA entity, DTO, and repository with pessimistic locking queries.
- `core.transaction`: transfer validation, asynchronous settlement, ledger entity, events, outbox-backed producer, RabbitMQ consumer, DTOs, and repository.
- `core.paymentrequest`: payment request creation, active lookup, delayed expiration event, outbox-backed producer, RabbitMQ consumer, entity, DTOs, and repository.
- `shared.outbox`: transactional event storage, claiming, publishing, retry, and RabbitMQ message creation.
- `shared.messaging`: processed-event table and idempotent consumer wrapper.
- `shared.notification`: SSE registry, Redis Pub/Sub notification fan-out, RabbitMQ notification consumer, controller, and DTOs.
- `shared.security` and `shared.services`: JWT cookie authentication, principal, Argon2 password hashing, and JWT handling.

## Runtime Responsibilities

The API handles authentication, account lookup, transfer initiation, asynchronous financial settlement, payment request lifecycle management, and realtime transaction notifications. Monetary values are represented as integer minor units in the observed DTOs and entities.

## Endpoints

| Method | Path | Purpose | Authentication |
| --- | --- | --- | --- |
| `POST` | `/user` | Create user, create account, set auth cookie | Public |
| `POST` | `/user/login` | Authenticate and set auth cookie | Public |
| `POST` | `/user/logout` | Clear auth cookie | Public |
| `GET` | `/user` | Decode and refresh token, return user | Cookie |
| `PATCH` | `/user` | Update authenticated user | Cookie |
| `DELETE` | `/user` | Delete authenticated user | Cookie |
| `GET` | `/account` | Return authenticated user's account | Cookie |
| `GET` | `/account/email` | Return account id by user email query parameter | Cookie |
| `POST` | `/transaction` | Validate and enqueue transfer creation | Cookie |
| `GET` | `/transaction` | Return pageable transactions for authenticated user | Cookie |
| `GET` | `/transaction/{id}` | Return one transaction visible to the user | Cookie |
| `POST` | `/payment-request` | Create payment request | Cookie |
| `GET` | `/payment-request/{id}` | Return active payment request | Cookie |
| `GET` | `/notifications/stream` | Open SSE notification stream | Cookie |
| `GET` | `/actuator/health` | Aggregate health endpoint | Public |
| `GET` | `/actuator/health/liveness` | Kubernetes/container liveness health when probes are enabled | Public |
| `GET` | `/actuator/health/readiness` | Readiness health including configured dependencies when probes are enabled | Public |

## Messaging

RabbitMQ uses the durable direct exchange `auronix.transaction.exchange`.

| Queue | Routing key | Role |
| --- | --- | --- |
| `auronix.transfer.create.queue` | `transfer.create` | Asynchronous transfer settlement trigger |
| `auronix.transaction.completed.queue` | `transaction.completed` | Transaction completion notification trigger |
| `auronix.payment-request.expiration.delay.queue` | `payment-request.expiration.delay` | Ten-minute delayed payment request expiration |
| `auronix.payment-request.expiration.queue` | `payment-request.expiration` | Expired payment request cleanup |

Domain services do not send RabbitMQ messages directly. Producers write `OutboxEvent` rows, and the scheduled outbox publisher later sends JSON messages with `messageId`, `eventId`, `eventType`, and `aggregateId` metadata.

## Observed Technical Decisions

- User creation also creates an account.
- Transfer creation performs synchronous validation, then stores a `transfer.create` event in the outbox.
- Transfer settlement is asynchronous and revalidates all critical financial checks in the consumer transaction.
- Account rows use JPA `@Version`; settlement also uses `PESSIMISTIC_WRITE` locks in deterministic account-id order.
- Payment requests expire through a RabbitMQ delay queue and dead-letter routing.
- SSE emitters are process-local; Redis stores connection metadata and distributes realtime notifications through Pub/Sub.
