# Infraestrutura

## Local

`compose.yaml` e a fonte de verdade para infraestrutura de desenvolvimento local. Ele inicia:

- `server`: imagem construida a partir do `Dockerfile`, porta `8080`.
- `db`: PostgreSQL 17 Alpine, banco `auronix`, porta `5432`, volume persistente nomeado.
- `message-br`: RabbitMQ 4 management, portas `5672` e `15672`, volume persistente nomeado.
- `cache`: Redis 8 Alpine, porta `6379`, AOF habilitado e volume persistente nomeado.

O Compose tambem define rede dedicada, restart policy e health checks para os servicos criticos. `depends_on` usa condicoes de saude para evitar tratar container apenas `running` como dependencia pronta.

## Imagem

O `Dockerfile` usa build multi-stage com Temurin 26:

- etapa de dependencias Maven;
- etapa de package;
- extracao por Spring Boot layertools;
- runtime JRE com usuario nao-root `appuser`.

A imagem expoe a porta `8080` e inicia a aplicacao via `JarLauncher`.

## Producao

Producao nao e representada pelo Docker Compose. O fluxo esperado e:

```text
AWS
|
Terraform
|
EKS
|
Kubernetes
```

A stack `infra/terraform/cluster` provisiona VPC e EKS. A stack `infra/terraform/app` aplica os manifests de workloads da aplicacao no cluster Kubernetes existente.

PostgreSQL, RabbitMQ e Redis de producao nao sao criados pelo ciclo de vida dos Pods da aplicacao. Os manifests de producao recebem endpoints externos por ConfigMap/Secret. Se RDS/Aurora, Amazon MQ ou ElastiCache forem adotados, isso deve entrar em Terraform explicitamente em uma etapa propria.

## Limites Atuais

- Terraform ainda nao provisiona RDS/Aurora.
- Terraform ainda nao provisiona RabbitMQ gerenciado.
- Terraform ainda nao provisiona Redis gerenciado.
- Os manifests locais de Postgres, RabbitMQ e Redis existem para validacao Kubernetes local, nao como arquitetura produtiva.
