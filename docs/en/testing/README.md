# Testing

## Frameworks

The project uses Maven-based automated tests with Spring Boot test dependencies, JUnit, Mockito, AssertJ, MockMvc, H2, Spring Security test support, Spring AMQP test support, and Spring Cloud Stream test binder.

## Commands

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

## Test Configuration

Tests use `src/test/resources/application.properties`, which configures:

- In-memory H2 database with PostgreSQL compatibility mode.
- Hibernate `create-drop`.
- RabbitMQ and Redis health checks disabled.
- RabbitMQ listeners disabled.
- Test-only JWT and cookie settings.

## Coverage Areas

The observed tests cover:

- Spring context startup.
- CORS and RabbitMQ configuration.
- JWT creation, validation, expiration, signature checks, and authentication filter behavior.
- User, account, transaction, payment request, and notification services.
- MockMvc tests for controllers.
- DTO validation rules.
- Entity mapping helper behavior such as response DTO conversion and lifecycle timestamp assignment.
- RabbitMQ producers and consumers through mocked collaborators.
- SSE registry behavior and notification delivery.
