# Testing

## Frameworks

The project uses Maven, JUnit Jupiter, Spring Boot test support, Mockito, AssertJ, MockMvc, Spring Security test support, Spring AMQP test support, H2, Spring Cloud Stream test binder, and Testcontainers PostgreSQL.

## Unit Tests

Most service, producer, consumer, DTO, entity, security, notification, outbox, and configuration tests use JUnit with Mockito and AssertJ. These tests verify local behavior such as validation, collaborator calls, event metadata, outbox state transitions, idempotent consumer branching, JWT handling, CORS, and RabbitMQ topology.

## Spring Controller and Integration Tests

Controller tests use `@WebMvcTest`, `@AutoConfigureMockMvc(addFilters = false)`, `MockMvc`, and `@MockitoBean` collaborators to verify HTTP status codes, JSON payloads, cookies, async SSE request handling, and route behavior.

`AuronixApplicationTests` uses `@SpringBootTest` to verify that the Spring context loads with the test configuration.

## H2 Test Layer

`src/test/resources/application.properties` configures H2 in memory with PostgreSQL compatibility mode and Hibernate `create-drop`. It also disables RabbitMQ listeners and Rabbit/Redis health checks, points RabbitMQ/Redis to invalid endpoints, disables the outbox publisher, and disables Redis subscription.

H2 is useful for fast Spring tests, but it is not the only persistence layer tested. It cannot fully prove PostgreSQL-specific locking, native SQL conflict handling, or check-constraint behavior.

## PostgreSQL with Testcontainers

`FinancePostgresIntegrationTest` uses `@Testcontainers(disabledWithoutDocker = true)` and a `postgres:17-alpine` container. It overrides datasource properties through `@DynamicPropertySource` and runs Hibernate `create-drop` against real PostgreSQL.

The current test verifies PostgreSQL-specific behavior:

- A negative account balance is rejected by the database check constraint.
- Concurrent duplicate `processed_events` inserts for the same `eventId` result in exactly one successful insert because of `on conflict (event_id) do nothing` and the uniqueness constraint.
- An outbox row created in the same transaction as a business write rolls back when the business transaction rolls back.

The current Testcontainers class does not execute the full transfer settlement path under concurrent transfers. The deterministic pessimistic locking behavior is covered by unit-level tests and repository code inspection, while PostgreSQL integration currently focuses on constraints, idempotency insertion atomicity, and transaction rollback.

## Mandatory Testcontainers Script

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-testcontainers.ps1
```

The script requires Docker by running `docker ps`, executes:

```powershell
.\mvnw.cmd -q -Dtest=FinancePostgresIntegrationTest test
```

Then it reads `target/surefire-reports/TEST-dev.joaopdias.auronix.integration.FinancePostgresIntegrationTest.xml` and fails if no tests were discovered or if `failures`, `errors`, or `skipped` are non-zero. CI performs the same mandatory check after the regular Maven test run.

## Standard Commands

```bash
./mvnw test
./mvnw package -DskipTests
```

On Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```
