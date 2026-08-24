# Aplicação

## Estrutura do Projeto

O código da aplicação fica em `src/main/java/dev/joaopdias/auronix`.

- `config`: Spring Security, CORS, topologia RabbitMQ e configuração Redis Pub/Sub.
- `core.user`: cadastro, login, renovação de token, atualização de perfil, remoção, DTOs, entidade e repository.
- `core.account`: criação e consulta de conta, entidade JPA, DTO e repository com queries de lock pessimista.
- `core.transaction`: validação de transferência, liquidação assíncrona, entidade de ledger, eventos, producer baseado em outbox, consumer RabbitMQ, DTOs e repository.
- `core.paymentrequest`: criação de cobrança, consulta ativa, evento de expiração atrasada, producer baseado em outbox, consumer RabbitMQ, entidade, DTOs e repository.
- `shared.outbox`: armazenamento transacional de eventos, claim, publicação, retry e criação de mensagens RabbitMQ.
- `shared.messaging`: tabela de eventos processados e wrapper de consumer idempotente.
- `shared.notification`: registro SSE, fan-out de notificação por Redis Pub/Sub, consumer RabbitMQ de notificação, controller e DTOs.
- `shared.security` e `shared.services`: autenticação JWT por cookie, principal, hash Argon2 e manipulação de JWT.

## Responsabilidades de Runtime

A API gerencia autenticação, consulta de contas, início de transferências, liquidação financeira assíncrona, ciclo de vida de cobranças e notificações de transação em tempo real. Valores monetários são representados como unidades menores inteiras nos DTOs e entidades observados.

## Endpoints

| Método | Caminho | Finalidade | Autenticação |
| --- | --- | --- | --- |
| `POST` | `/user` | Criar usuário, criar conta e definir cookie | Público |
| `POST` | `/user/login` | Autenticar e definir cookie | Público |
| `POST` | `/user/logout` | Limpar cookie | Público |
| `GET` | `/user` | Decodificar e renovar token, retornando usuário | Cookie |
| `PATCH` | `/user` | Atualizar usuário autenticado | Cookie |
| `DELETE` | `/user` | Remover usuário autenticado | Cookie |
| `GET` | `/account` | Retornar conta do usuário autenticado | Cookie |
| `GET` | `/account/email` | Retornar id da conta por e-mail via query parameter | Cookie |
| `POST` | `/transaction` | Validar e enfileirar criação de transferência | Cookie |
| `GET` | `/transaction` | Retornar transações paginadas do usuário autenticado | Cookie |
| `GET` | `/transaction/{id}` | Retornar uma transação visível ao usuário | Cookie |
| `POST` | `/payment-request` | Criar cobrança | Cookie |
| `GET` | `/payment-request/{id}` | Retornar cobrança ativa | Cookie |
| `GET` | `/notifications/stream` | Abrir stream SSE de notificações | Cookie |
| `GET` | `/actuator/health` | Health agregado | Público |
| `GET` | `/actuator/health/liveness` | Liveness para container/Kubernetes quando probes estão habilitados | Público |
| `GET` | `/actuator/health/readiness` | Readiness com dependências configuradas quando probes estão habilitados | Público |

## Mensageria

RabbitMQ usa o direct exchange durável `auronix.transaction.exchange`.

| Fila | Routing key | Papel |
| --- | --- | --- |
| `auronix.transfer.create.queue` | `transfer.create` | Gatilho de liquidação assíncrona de transferência |
| `auronix.transaction.completed.queue` | `transaction.completed` | Gatilho de notificação de transação concluída |
| `auronix.payment-request.expiration.delay.queue` | `payment-request.expiration.delay` | Expiração atrasada de cobrança em dez minutos |
| `auronix.payment-request.expiration.queue` | `payment-request.expiration` | Limpeza de cobrança expirada |

Services de domínio não enviam mensagens RabbitMQ diretamente. Producers gravam linhas `OutboxEvent`, e o publisher agendado envia depois mensagens JSON com metadata `messageId`, `eventId`, `eventType` e `aggregateId`.

## Decisões Técnicas Observadas

- Criação de usuário também cria uma conta.
- Criação de transferência faz validação síncrona e grava um evento `transfer.create` na outbox.
- Liquidação de transferência é assíncrona e revalida checagens financeiras críticas na transação do consumer.
- Contas usam JPA `@Version`; a liquidação também usa locks `PESSIMISTIC_WRITE` em ordem determinística de id da conta.
- Cobranças expiram por fila RabbitMQ atrasada e roteamento dead-letter.
- Emitters SSE são locais ao processo; Redis armazena metadata de conexão e distribui notificações realtime via Pub/Sub.
