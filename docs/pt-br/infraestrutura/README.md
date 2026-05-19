# Infraestrutura

## Infraestrutura Local

`compose.yaml` define um runtime local com quatro servicos:

- `server`: constroi a imagem do projeto atual e expoe a porta `8080`.
- `db`: PostgreSQL 17 Alpine, banco `auronix`, exposto na porta `5432`, com volume nomeado.
- `message-br`: imagem RabbitMQ 4 management, AMQP em `5672`, interface de gestao em `15672`.
- `redis`: Redis 8 Alpine exposto em `6379`.

Health checks estao configurados para PostgreSQL, RabbitMQ e Redis, e o servidor aguarda essas dependencias antes de iniciar.

## Imagem de Container

O `Dockerfile` usa build multi-stage:

- Estagio de dependencias baseado em `eclipse-temurin:26-jdk-jammy`.
- Estagio de package executando Maven package com testes ignorados.
- Estagio de extracao de camadas com Spring Boot layertools.
- Estagio de runtime baseado em `eclipse-temurin:26-jre-jammy`.

A imagem final cria e executa com usuario nao-root `appuser`, expoe a porta `8080` e inicia o jar Spring Boot por `JarLauncher`.

## Infraestrutura de Cluster

O Terraform provisiona infraestrutura AWS em `infra/terraform/cluster`:

- Provider AWS na regiao configurada.
- VPC por `terraform-aws-modules/vpc/aws`.
- EKS por `terraform-aws-modules/eks/aws`.
- Subnets privadas e publicas derivadas do CIDR da VPC e da quantidade de zonas de disponibilidade.
- NAT Gateway habilitado, com `single_nat_gateway` configuravel.
- Node group gerenciado do EKS com tipos de instancia e quantidades de nos configuraveis.

## Infraestrutura da Aplicacao

Os recursos de runtime da aplicacao sao definidos em `k8s` e aplicados diretamente ou por `infra/terraform/app`. Eles incluem API, PostgreSQL, RabbitMQ, Redis, metrics-server, HPA, ConfigMap e formato de Secret.

Observacao importante: o PostgreSQL no Kubernetes usa `emptyDir`. Isso e aceitavel para estudo, validacao ou ambientes descartaveis. Para ambientes similares a producao, recomenda-se volume persistente e migrations controladas, como Flyway ou Liquibase.
