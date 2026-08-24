# Configuration

## Application Properties

Runtime configuration is read from environment variables with defaults in `src/main/resources/application.properties`.

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP port | `8080` |
| `SERVER_SHUTDOWN` | Spring shutdown mode | `graceful` |
| `SHUTDOWN_TIMEOUT` | Per-shutdown-phase timeout | `30s` |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/auronix` |
| `DATABASE_USERNAME` | PostgreSQL user | `postgres` |
| `DATABASE_PASSWORD` | PostgreSQL password | `postgres` |
| `JPA_DDL_AUTO` | Hibernate schema strategy | `update` |
| `JPA_SHOW_SQL` | SQL logging | `true` |
| `JPA_FORMAT_SQL` | SQL formatting | `true` |
| `SQL_INIT_MODE` | SQL initialization | `never` |
| `RABBITMQ_URL` | RabbitMQ AMQP URL | `amqp://localhost:5672/` |
| `RABBITMQ_USERNAME` | RabbitMQ user | `user` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `user` |
| `RABBITMQ_RETRY_ENABLED` | Listener retry switch | `true` |
| `RABBITMQ_RETRY_INITIAL_INTERVAL` | Initial listener retry interval | `1000` |
| `RABBITMQ_RETRY_MAX_ATTEMPTS` | Maximum listener retry attempts | `3` |
| `RABBITMQ_RETRY_MAX_INTERVAL` | Maximum listener retry interval | `10000` |
| `RABBITMQ_RETRY_MULTIPLIER` | Listener retry multiplier | `2` |
| `REDIS_URL` | Redis URL | `redis://localhost:6379` |
| `MANAGEMENT_HEALTH_PROBES_ENABLED` | Enables liveness/readiness groups | `false` |
| `MANAGEMENT_HEALTH_LIVENESS_INCLUDE` | Liveness indicators | `livenessState` |
| `MANAGEMENT_HEALTH_READINESS_INCLUDE` | Readiness indicators | `readinessState,db,rabbit,redis` |
| `APP_INSTANCE_ID` | Replica id stored in SSE metadata | random UUID |
| `CLIENT_URLS` | Semicolon-separated CORS origins | `http://localhost:4200` |
| `JWT_SECRET` | JWT HMAC signing secret | `auronix` |
| `JWT_EXPIRES_IN_MINUTES` | JWT expiration duration | `120` |
| `COOKIE_SECURE` | HTTPS-only auth cookie flag | `false` |
| `COOKIE_SAME_SITE` | Auth cookie SameSite policy | `Strict` |

`app.outbox.enabled`, `app.outbox.batch-size`, `app.outbox.publish-delay-ms`, and `app.realtime.redis-subscribe-enabled` are also read by components, with defaults in code when absent.

## Docker Compose

Compose provides local development values:

- `DATABASE_URL=jdbc:postgresql://db:5432/auronix`
- `RABBITMQ_URL=amqp://message-br:5672/`
- `REDIS_URL=redis://cache:6379`
- `MANAGEMENT_HEALTH_PROBES_ENABLED=true`
- JPA schema update and SQL logging enabled for local iteration.

These values are not production settings.

## Kubernetes

`auronix-config` contains non-sensitive runtime settings. `auronix-secrets` contains database credentials, RabbitMQ credentials, and `JWT_SECRET`. Production Kustomize config uses placeholders for external PostgreSQL, RabbitMQ, and Redis endpoints and `JPA_DDL_AUTO=validate`.

## Tests

`src/test/resources/application.properties` uses H2 in PostgreSQL compatibility mode for most tests, disables RabbitMQ listeners and Rabbit/Redis health checks, points RabbitMQ/Redis to invalid endpoints, disables the outbox publisher, disables Redis subscription, and uses test-only security values.
