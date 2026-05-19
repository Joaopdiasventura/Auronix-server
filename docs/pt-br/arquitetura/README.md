# Arquitetura

## Visao Geral

Auronix e organizado como um backend Spring Boot em camadas. Controllers HTTP recebem requisicoes, services aplicam regras de negocio, repositories Spring Data persistem entidades no PostgreSQL, RabbitMQ transporta eventos assincronos e Redis armazena metadados de conexoes SSE de notificacao.

```mermaid
flowchart TD
    Client[Cliente] --> Controllers[Controllers REST]
    Controllers --> Services[Services de dominio]
    Services --> Repositories[Repositories Spring Data]
    Repositories --> Postgres[(PostgreSQL)]
    Services --> Producers[Producers RabbitMQ]
    Producers --> Rabbit[(RabbitMQ Direct Exchange)]
    Rabbit --> Consumers[Consumers RabbitMQ]
    Consumers --> Services
    Services --> Notifications[Servico de notificacao]
    Notifications --> SSE[Registro SSE]
    SSE --> Redis[(Redis)]
    SSE --> Client
```

## Componentes Principais

- Modulo de usuarios: cadastro, login, renovacao de token por `/user`, atualizacao de perfil e remocao.
- Modulo de contas: consulta de conta pelo usuario autenticado e busca de conta por e-mail de usuario.
- Modulo de transacoes: valida solicitacoes de transferencia, publica eventos assincronos, registra ledger e publica eventos de conclusao.
- Modulo de cobrancas: cria cobrancas, consulta cobrancas ativas e remove cobrancas expiradas por fluxo atrasado no RabbitMQ.
- Modulo de notificacoes: expoe stream SSE e envia notificacoes de transacoes concluidas aos usuarios envolvidos.
- Seguranca compartilhada: cria e valida JWTs, gera hashes de senha com Argon2 e autentica requisicoes pelo cookie `access_token`.

## Fluxos Principais

```mermaid
sequenceDiagram
    participant C as Cliente
    participant API as Auronix API
    participant MQ as RabbitMQ
    participant DB as PostgreSQL
    participant SSE as Stream SSE
    C->>API: POST /transaction
    API->>DB: Valida contas de origem e destino
    API->>MQ: Publica transfer.create
    MQ->>API: Consome evento de criacao
    API->>DB: Atualiza saldos e salva ledger
    API->>MQ: Publica transaction.completed apos commit
    MQ->>API: Consome evento de conclusao
    API->>SSE: Envia notificacao transaction.completed
    SSE-->>C: Payload do evento
```

```mermaid
sequenceDiagram
    participant C as Cliente
    participant API as Auronix API
    participant MQ as RabbitMQ
    participant DB as PostgreSQL
    C->>API: POST /payment-request
    API->>DB: Salva cobranca
    API->>MQ: Publica na fila atrasada de expiracao
    MQ-->>MQ: Aguarda TTL configurado
    MQ->>API: Encaminha evento de expiracao via dead letter
    API->>DB: Remove cobranca se estiver expirada
```

## Relacao com Infraestrutura

O Terraform esta dividido em duas stacks. A stack de cluster provisiona VPC e EKS na AWS. A stack de aplicacao le os manifests YAML de `k8s` e os aplica pelo provider Kubernetes do Terraform.
