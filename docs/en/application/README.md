# Application

## Project Structure

The application code is under `src/main/java/dev/joaopdias/auronix`.

- `config`: Spring Security, CORS, RabbitMQ exchange/queue/binding configuration.
- `core.user`: user creation, login, profile updates, deletion, DTOs, entity, and repository.
- `core.account`: account creation and lookup, account entity, DTO, and repository.
- `core.transaction`: transfer request validation, asynchronous transfer processing, ledger entity, events, producer, consumer, DTOs, and repository.
- `core.paymentrequest`: payment request creation, active lookup, expiration event, producer, consumer, entity, DTOs, and repository.
- `shared.security`: request authentication filter and authenticated principal.
- `shared.services`: password hashing and JWT creation/validation.
- `shared.notification`: SSE registration, transaction notification delivery, consumer, controller, and DTOs.

## Runtime Responsibilities

The API handles user authentication, account lookup, transfer initiation, asynchronous ledger updates, payment request lifecycle management, and real-time transaction notifications. Monetary values are represented as integer minor units in the observed DTOs and entities.

## Endpoints

| Method | Path | Purpose | Authentication |
| --- | --- | --- | --- |
| `POST` | `/user` | Create user, create account, set auth cookie | Public |
| `POST` | `/user/login` | Authenticate user and set auth cookie | Public |
| `POST` | `/user/logout` | Clear auth cookie | Public |
| `GET` | `/user` | Decode and refresh token, return user | Cookie |
| `PATCH` | `/user` | Update authenticated user | Cookie |
| `DELETE` | `/user` | Delete authenticated user | Cookie |
| `GET` | `/account` | Return authenticated user account | Cookie |
| `GET` | `/account/email` | Return account id by user email query parameter | Cookie |
| `POST` | `/transaction` | Validate and enqueue transfer creation | Cookie |
| `GET` | `/transaction` | Return pageable transactions for authenticated user | Cookie |
| `GET` | `/transaction/{id}` | Return one transaction visible to the user | Cookie |
| `POST` | `/payment-request` | Create payment request | Cookie |
| `GET` | `/payment-request/{id}` | Return active payment request | Cookie |
| `GET` | `/notifications/stream` | Open SSE notification stream | Cookie |
| `GET` | `/actuator/health` | Health endpoint for probes | Public |

## Messaging

RabbitMQ uses the direct exchange `auronix.transaction.exchange`.

| Queue | Routing key | Role |
| --- | --- | --- |
| `auronix.transfer.create.queue` | `transfer.create` | Asynchronous transfer creation |
| `auronix.transaction.completed.queue` | `transaction.completed` | Transaction completion notification |
| `auronix.payment-request.expiration.delay.queue` | `payment-request.expiration.delay` | Delayed payment request expiration |
| `auronix.payment-request.expiration.queue` | `payment-request.expiration` | Expired payment request cleanup |

Messages are converted with `JacksonJsonMessageConverter`.

## Observed Technical Decisions

- Accounts are created with an initial balance during user registration.
- Account balances use optimistic locking through a JPA `@Version` field.
- Transaction completion events are published after database commit.
- Payment requests expire after ten minutes through a delayed RabbitMQ queue and dead-letter routing.
- SSE connection metadata is stored in Redis with a 30-minute TTL; active emitters remain local to the application instance.
