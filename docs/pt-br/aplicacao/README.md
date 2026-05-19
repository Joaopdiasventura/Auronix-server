# Aplicacao

## Estrutura do Projeto

O codigo da aplicacao fica em `src/main/java/dev/joaopdias/auronix`.

- `config`: configuracao de Spring Security, CORS, exchange, filas e bindings RabbitMQ.
- `core.user`: criacao de usuario, login, atualizacao de perfil, remocao, DTOs, entidade e repository.
- `core.account`: criacao e consulta de conta, entidade, DTO e repository.
- `core.transaction`: validacao de transferencia, processamento assincrono, entidade de ledger, eventos, producer, consumer, DTOs e repository.
- `core.paymentrequest`: criacao de cobranca, consulta ativa, evento de expiracao, producer, consumer, entidade, DTOs e repository.
- `shared.security`: filtro de autenticacao e principal autenticado.
- `shared.services`: hash de senha e criacao/validacao de JWT.
- `shared.notification`: registro SSE, entrega de notificacoes, consumer, controller e DTOs.

## Responsabilidades de Runtime

A API gerencia autenticacao de usuarios, consulta de contas, inicio de transferencias, atualizacao assincrona do ledger, ciclo de vida de cobrancas e notificacoes de transacoes em tempo real. Valores monetarios sao representados como unidades menores inteiras nos DTOs e entidades observados.

## Endpoints

| Metodo | Caminho | Finalidade | Autenticacao |
| --- | --- | --- | --- |
| `POST` | `/user` | Criar usuario, criar conta e definir cookie de autenticacao | Publico |
| `POST` | `/user/login` | Autenticar usuario e definir cookie | Publico |
| `POST` | `/user/logout` | Limpar cookie de autenticacao | Publico |
| `GET` | `/user` | Decodificar e renovar token, retornando usuario | Cookie |
| `PATCH` | `/user` | Atualizar usuario autenticado | Cookie |
| `DELETE` | `/user` | Remover usuario autenticado | Cookie |
| `GET` | `/account` | Retornar conta do usuario autenticado | Cookie |
| `GET` | `/account/email` | Retornar id da conta por e-mail via query parameter | Cookie |
| `POST` | `/transaction` | Validar e enfileirar criacao de transferencia | Cookie |
| `GET` | `/transaction` | Retornar transacoes paginadas do usuario autenticado | Cookie |
| `GET` | `/transaction/{id}` | Retornar uma transacao visivel ao usuario | Cookie |
| `POST` | `/payment-request` | Criar cobranca | Cookie |
| `GET` | `/payment-request/{id}` | Retornar cobranca ativa | Cookie |
| `GET` | `/notifications/stream` | Abrir stream SSE de notificacoes | Cookie |
| `GET` | `/actuator/health` | Health endpoint para probes | Publico |

## Mensageria

O RabbitMQ usa o direct exchange `auronix.transaction.exchange`.

| Fila | Routing key | Papel |
| --- | --- | --- |
| `auronix.transfer.create.queue` | `transfer.create` | Criacao assincrona de transferencia |
| `auronix.transaction.completed.queue` | `transaction.completed` | Notificacao de transacao concluida |
| `auronix.payment-request.expiration.delay.queue` | `payment-request.expiration.delay` | Expiracao atrasada de cobranca |
| `auronix.payment-request.expiration.queue` | `payment-request.expiration` | Limpeza de cobranca expirada |

As mensagens sao convertidas com `JacksonJsonMessageConverter`.

## Decisoes Tecnicas Observadas

- Contas sao criadas com saldo inicial durante o cadastro do usuario.
- Saldos de conta usam controle otimista por campo JPA `@Version`.
- Eventos de transacao concluida sao publicados apos commit no banco.
- Cobrancas expiram apos dez minutos por fila atrasada RabbitMQ e roteamento dead-letter.
- Metadados de conexoes SSE sao armazenados no Redis com TTL de 30 minutos; emissores ativos permanecem locais na instancia da aplicacao.
