# Configuracao

## Propriedades da Aplicacao

A aplicacao le configuracoes de runtime por variaveis de ambiente com defaults em `src/main/resources/application.properties`.

| Variavel | Finalidade | Obrigatoria para uso similar a producao |
| --- | --- | --- |
| `PORT` | Porta HTTP do servidor | Opcional |
| `DATABASE_URL` | URL JDBC do PostgreSQL | Sim |
| `DATABASE_USERNAME` | Usuario do PostgreSQL | Sim |
| `DATABASE_PASSWORD` | Senha do PostgreSQL | Sim |
| `JPA_DDL_AUTO` | Estrategia de schema do Hibernate | Depende do ambiente |
| `JPA_SHOW_SQL` | Flag de log SQL | Opcional |
| `JPA_FORMAT_SQL` | Flag de formatacao SQL | Opcional |
| `SQL_INIT_MODE` | Modo de inicializacao SQL | Opcional |
| `RABBITMQ_URL` | URL AMQP do RabbitMQ | Sim |
| `RABBITMQ_RETRY_ENABLED` | Controle de retry do listener | Opcional |
| `RABBITMQ_RETRY_INITIAL_INTERVAL` | Intervalo inicial de retry | Opcional |
| `RABBITMQ_RETRY_MAX_ATTEMPTS` | Maximo de tentativas | Opcional |
| `RABBITMQ_RETRY_MAX_INTERVAL` | Intervalo maximo de retry | Opcional |
| `RABBITMQ_RETRY_MULTIPLIER` | Multiplicador de retry | Opcional |
| `REDIS_URL` | URL do Redis | Sim |
| `APP_INSTANCE_ID` | Id da instancia usado em metadados SSE | Opcional |
| `CLIENT_URLS` | Origens CORS separadas por ponto e virgula | Sim para clientes publicados |
| `JWT_SECRET` | Segredo HMAC para assinatura JWT | Sim |
| `JWT_EXPIRES_IN_MINUTES` | Duracao de expiracao do token | Opcional |
| `COOKIE_SECURE` | Marca o cookie de autenticacao como secure | Sim para deploys HTTPS |
| `COOKIE_SAME_SITE` | Configuracao SameSite do cookie | Opcional |

Nao commite secrets de producao. Use secrets da plataforma, Kubernetes Secrets ou outro mecanismo de gestao de segredos para valores sensiveis.

## Docker Compose

O Compose fornece valores locais para banco, RabbitMQ, Redis, opcoes JPA e porta do servidor. Esses valores sao orientados a desenvolvimento e devem ser substituidos em ambientes compartilhados ou similares a producao.

## Kubernetes

`auronix-config` armazena configuracoes nao sensiveis de runtime. `auronix-secrets` define chaves secretas para credenciais de banco, credenciais RabbitMQ e assinatura JWT. Esta documentacao descreve as chaves sem apresentar valores secretos como credenciais recomendadas.

## Testes

`src/test/resources/application.properties` usa H2 em modo de compatibilidade com PostgreSQL, desabilita health checks de RabbitMQ e Redis, aponta RabbitMQ e Redis para enderecos locais invalidos e define propriedades de seguranca exclusivas de teste.
