# Configuração

## Propriedades da Aplicação

A configuração de runtime vem de variáveis de ambiente com defaults em `src/main/resources/application.properties`.

| Variavel | Finalidade | Default |
| --- | --- | --- |
| `PORT` | Porta HTTP | `8080` |
| `SERVER_SHUTDOWN` | Modo de shutdown Spring | `graceful` |
| `SHUTDOWN_TIMEOUT` | Timeout por fase de shutdown | `30s` |
| `DATABASE_URL` | URL JDBC do PostgreSQL | `jdbc:postgresql://localhost:5432/auronix` |
| `DATABASE_USERNAME` | Usuario PostgreSQL | `postgres` |
| `DATABASE_PASSWORD` | Senha PostgreSQL | `postgres` |
| `JPA_DDL_AUTO` | Estrategia de schema Hibernate | `update` |
| `JPA_SHOW_SQL` | Log SQL | `true` |
| `JPA_FORMAT_SQL` | Formatacao SQL | `true` |
| `SQL_INIT_MODE` | Inicializacao SQL | `never` |
| `RABBITMQ_URL` | URL AMQP RabbitMQ | `amqp://localhost:5672/` |
| `RABBITMQ_USERNAME` | Usuario RabbitMQ | `user` |
| `RABBITMQ_PASSWORD` | Senha RabbitMQ | `user` |
| `RABBITMQ_RETRY_ENABLED` | Habilita retry de listener | `true` |
| `RABBITMQ_RETRY_INITIAL_INTERVAL` | Intervalo inicial de retry | `1000` |
| `RABBITMQ_RETRY_MAX_ATTEMPTS` | Maximo de tentativas de listener | `3` |
| `RABBITMQ_RETRY_MAX_INTERVAL` | Intervalo máximo de retry | `10000` |
| `RABBITMQ_RETRY_MULTIPLIER` | Multiplicador de retry | `2` |
| `REDIS_URL` | URL Redis | `redis://localhost:6379` |
| `MANAGEMENT_HEALTH_PROBES_ENABLED` | Habilita grupos liveness/readiness | `false` |
| `MANAGEMENT_HEALTH_LIVENESS_INCLUDE` | Indicadores de liveness | `livenessState` |
| `MANAGEMENT_HEALTH_READINESS_INCLUDE` | Indicadores de readiness | `readinessState,db,rabbit,redis` |
| `APP_INSTANCE_ID` | Id da réplica salvo em metadata SSE | UUID aleatorio |
| `CLIENT_URLS` | Origens CORS separadas por ponto e vírgula | `http://localhost:4200` |
| `JWT_SECRET` | Segredo HMAC para JWT | `auronix` |
| `JWT_EXPIRES_IN_MINUTES` | Duracao do JWT | `120` |
| `COOKIE_SECURE` | Cookie apenas HTTPS | `false` |
| `COOKIE_SAME_SITE` | Politica SameSite do cookie | `Strict` |

`app.outbox.enabled`, `app.outbox.batch-size`, `app.outbox.publish-delay-ms` e `app.realtime.redis-subscribe-enabled` também são lidos por componentes, com defaults no código quando ausentes.

## Docker Compose

Compose fornece valores locais:

- `DATABASE_URL=jdbc:postgresql://db:5432/auronix`
- `RABBITMQ_URL=amqp://message-br:5672/`
- `REDIS_URL=redis://cache:6379`
- `MANAGEMENT_HEALTH_PROBES_ENABLED=true`
- update de schema JPA e log SQL habilitados para iteração local.

Esses valores não são configuração de produção.

## Kubernetes

`auronix-config` contém configurações não sensíveis. `auronix-secrets` contém credenciais de banco, credenciais RabbitMQ e `JWT_SECRET`. A configuração Kustomize de produção usa placeholders para endpoints externos de PostgreSQL, RabbitMQ e Redis e `JPA_DDL_AUTO=validate`.

## Testes

`src/test/resources/application.properties` usa H2 em memória com modo PostgreSQL para a maioria dos testes, desabilita listeners RabbitMQ e health checks Rabbit/Redis, aponta RabbitMQ/Redis para endpoints inválidos, desabilita o publisher da outbox, desabilita a subscription Redis e usa valores de segurança exclusivos de teste.
