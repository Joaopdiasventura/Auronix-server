# Testes

## Frameworks

O projeto usa Maven, JUnit Jupiter, suporte de testes Spring Boot, Mockito, AssertJ, MockMvc, suporte Spring Security test, suporte Spring AMQP test, H2, Spring Cloud Stream test binder e Testcontainers PostgreSQL.

## Testes Unitários

A maioria dos testes de services, producers, consumers, DTOs, entidades, segurança, notificação, outbox e configuração usa JUnit com Mockito e AssertJ. Esses testes verificam comportamento local, como validação, chamadas a colaboradores, metadata de eventos, transições de estado da outbox, ramificações de consumer idempotente, JWT, CORS e topologia RabbitMQ.

## Testes Spring de Controller e Integração

Testes de controller usam `@WebMvcTest`, `@AutoConfigureMockMvc(addFilters = false)`, `MockMvc` e colaboradores `@MockitoBean` para verificar status HTTP, payloads JSON, cookies, requisições SSE assíncronas e comportamento de rotas.

`AuronixApplicationTests` usa `@SpringBootTest` para verificar que o contexto Spring sobe com a configuração de teste.

## Camada H2

`src/test/resources/application.properties` configura H2 em memória com modo de compatibilidade PostgreSQL e Hibernate `create-drop`. Também desabilita listeners RabbitMQ e health checks Rabbit/Redis, aponta RabbitMQ/Redis para endpoints inválidos, desabilita o publisher da outbox e desabilita a subscription Redis.

H2 é útil para testes Spring rápidos, mas não é a única camada de persistência testada. Ele não prova completamente locking, SQL nativo com conflict handling ou check constraints específicas de PostgreSQL.

## PostgreSQL com Testcontainers

`FinancePostgresIntegrationTest` usa `@Testcontainers(disabledWithoutDocker = true)` e um container `postgres:17-alpine`. Ele sobrescreve propriedades de datasource com `@DynamicPropertySource` e roda Hibernate `create-drop` contra PostgreSQL real.

O teste atual verifica comportamento específico de PostgreSQL:

- Saldo negativo de conta é rejeitado pela check constraint do banco.
- Inserts concorrentes duplicados em `processed_events` para o mesmo `eventId` resultam em exatamente um insert bem-sucedido por causa de `on conflict (event_id) do nothing` e da constraint única.
- Uma linha de outbox criada na mesma transação que uma escrita de negócio faz rollback quando a transação de negócio faz rollback.

A classe Testcontainers atual não executa o fluxo completo de liquidação de transferências concorrentes. O locking pessimista determinístico é coberto por testes unitários e inspeção do código do repository, enquanto a integração PostgreSQL atual foca constraints, atomicidade do insert de idempotência e rollback transacional.

## Script Obrigatório de Testcontainers

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-testcontainers.ps1
```

O script exige Docker por meio de `docker ps`, executa:

```powershell
.\mvnw.cmd -q -Dtest=FinancePostgresIntegrationTest test
```

Depois lê `target/surefire-reports/TEST-dev.joaopdias.auronix.integration.FinancePostgresIntegrationTest.xml` e falha se nenhum teste for descoberto ou se `failures`, `errors` ou `skipped` forem diferentes de zero. O CI executa a mesma verificação obrigatória depois do Maven test regular.

## Comandos Padrão

```bash
./mvnw test
./mvnw package -DskipTests
```

No Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
```
