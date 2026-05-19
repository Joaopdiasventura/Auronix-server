# Architecture

## Overview

Auronix is organized as a layered Spring Boot backend. HTTP controllers receive client requests, service classes enforce business rules, Spring Data repositories persist entities in PostgreSQL, RabbitMQ transports asynchronous events, and Redis stores metadata for active SSE notification connections.

```mermaid
flowchart TD
    Client[Client] --> Controllers[REST Controllers]
    Controllers --> Services[Domain Services]
    Services --> Repositories[Spring Data Repositories]
    Repositories --> Postgres[(PostgreSQL)]
    Services --> Producers[RabbitMQ Producers]
    Producers --> Rabbit[(RabbitMQ Direct Exchange)]
    Rabbit --> Consumers[RabbitMQ Consumers]
    Consumers --> Services
    Services --> Notifications[Notification Service]
    Notifications --> SSE[SSE Registry]
    SSE --> Redis[(Redis)]
    SSE --> Client
```

## Main Components

- User module: registration, login, token refresh through `/user`, profile update, and deletion.
- Account module: account lookup by authenticated user and account lookup by user email.
- Transaction module: validates transfer requests, publishes asynchronous transfer creation events, records ledger transactions, and publishes completion events.
- Payment request module: creates payment requests, retrieves active requests, and removes expired requests through a delayed RabbitMQ flow.
- Notification module: exposes an SSE stream and sends transaction completion notifications to involved users.
- Shared security: creates and validates JWTs, hashes passwords with Argon2, and authenticates requests from the `access_token` cookie.

## Main Flows

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Auronix API
    participant MQ as RabbitMQ
    participant DB as PostgreSQL
    participant SSE as SSE Stream
    C->>API: POST /transaction
    API->>DB: Validate payer and payee accounts
    API->>MQ: Publish transfer.create
    MQ->>API: Consume transfer create event
    API->>DB: Update balances and save ledger entry
    API->>MQ: Publish transaction.completed after commit
    MQ->>API: Consume completion event
    API->>SSE: Send transaction.completed notification
    SSE-->>C: Event payload
```

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Auronix API
    participant MQ as RabbitMQ
    participant DB as PostgreSQL
    C->>API: POST /payment-request
    API->>DB: Save payment request
    API->>MQ: Publish to delayed expiration queue
    MQ-->>MQ: Wait configured TTL
    MQ->>API: Dead-letter as expiration event
    API->>DB: Delete request if expired
```

## Infrastructure Relationship

Terraform is split into two stacks. The cluster stack provisions the AWS VPC and EKS cluster. The app stack reads the Kubernetes YAML manifests from `k8s` and applies them through the Terraform Kubernetes provider.
