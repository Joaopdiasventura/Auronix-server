# Configuration

## Application Properties

The application reads runtime configuration from environment variables with defaults in `src/main/resources/application.properties`.

| Variable | Purpose | Required for production-like use |
| --- | --- | --- |
| `PORT` | HTTP server port | Optional |
| `DATABASE_URL` | PostgreSQL JDBC URL | Yes |
| `DATABASE_USERNAME` | PostgreSQL username | Yes |
| `DATABASE_PASSWORD` | PostgreSQL password | Yes |
| `JPA_DDL_AUTO` | Hibernate schema strategy | Environment-dependent |
| `JPA_SHOW_SQL` | SQL logging flag | Optional |
| `JPA_FORMAT_SQL` | SQL formatting flag | Optional |
| `SQL_INIT_MODE` | SQL initialization mode | Optional |
| `RABBITMQ_URL` | RabbitMQ AMQP URL | Yes |
| `RABBITMQ_RETRY_ENABLED` | Listener retry switch | Optional |
| `RABBITMQ_RETRY_INITIAL_INTERVAL` | Initial retry interval | Optional |
| `RABBITMQ_RETRY_MAX_ATTEMPTS` | Maximum retry attempts | Optional |
| `RABBITMQ_RETRY_MAX_INTERVAL` | Maximum retry interval | Optional |
| `RABBITMQ_RETRY_MULTIPLIER` | Retry multiplier | Optional |
| `REDIS_URL` | Redis URL | Yes |
| `APP_INSTANCE_ID` | Instance id used in SSE metadata | Optional |
| `CLIENT_URLS` | Semicolon-separated CORS origins | Yes for deployed clients |
| `JWT_SECRET` | JWT HMAC signing secret | Yes |
| `JWT_EXPIRES_IN_MINUTES` | Token expiration duration | Optional |
| `COOKIE_SECURE` | Marks auth cookie as secure | Yes for HTTPS deployments |
| `COOKIE_SAME_SITE` | Auth cookie SameSite setting | Optional |

Do not commit production secrets. Use platform secrets, Kubernetes Secrets, or another secret management mechanism for sensitive values.

## Docker Compose

Compose provides local values for database, RabbitMQ, Redis, JPA options, and server port. These values are development-oriented and should be replaced for shared or production-like environments.

## Kubernetes

`auronix-config` stores non-sensitive runtime settings. `auronix-secrets` defines secret keys for database credentials, RabbitMQ credentials, and JWT signing. The documentation intentionally describes the keys without presenting secret values as recommended credentials.

## Tests

`src/test/resources/application.properties` uses H2 in PostgreSQL compatibility mode, disables RabbitMQ and Redis health checks, points RabbitMQ and Redis to invalid local addresses, and sets test-only security properties.
