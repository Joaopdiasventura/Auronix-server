# Infrastructure

## Local Infrastructure

`compose.yaml` defines a local runtime with four services:

- `server`: builds the current project image and exposes port `8080`.
- `db`: PostgreSQL 17 Alpine, database `auronix`, exposed on port `5432`, with a named volume.
- `message-br`: RabbitMQ 4 management image, AMQP on `5672`, management UI on `15672`.
- `redis`: Redis 8 Alpine exposed on `6379`.

Health checks are configured for PostgreSQL, RabbitMQ, and Redis, and the server waits for these dependencies before startup.

## Container Image

The `Dockerfile` uses a multi-stage build:

- Dependency stage based on `eclipse-temurin:26-jdk-jammy`.
- Package stage running Maven package with tests skipped.
- Layer extraction stage using Spring Boot layertools.
- Runtime stage based on `eclipse-temurin:26-jre-jammy`.

The final image creates and runs as a non-root `appuser`, exposes port `8080`, and starts the Spring Boot jar through `JarLauncher`.

## Cluster Infrastructure

Terraform provisions AWS infrastructure in `infra/terraform/cluster`:

- AWS provider in the configured region.
- VPC through `terraform-aws-modules/vpc/aws`.
- EKS through `terraform-aws-modules/eks/aws`.
- Private and public subnets derived from the configured VPC CIDR and availability zone count.
- NAT Gateway enabled, with `single_nat_gateway` configurable.
- EKS managed node group using configurable instance types and node counts.

## Application Infrastructure

Runtime application resources are defined in `k8s` and applied directly or through `infra/terraform/app`. They include the API, PostgreSQL, RabbitMQ, Redis, metrics-server, HPA, ConfigMap, and Secret shape.

Important observation: PostgreSQL in Kubernetes uses `emptyDir`. This is acceptable for study, validation, or disposable environments. For production-like environments, a persistent volume and controlled migrations such as Flyway or Liquibase are recommended.
