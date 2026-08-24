# Operations

## Environment Model

```text
Local development:
Docker Compose -> server, db, message-br, cache

Local Kubernetes validation:
Kind -> Kustomize local overlay with in-cluster dependencies

Production Kubernetes manifests:
Auronix workloads -> external PostgreSQL, RabbitMQ, Redis endpoints

Current GitHub Actions main deployment:
self-hosted Docker runner -> one auronix-server container by published digest
```

Docker Compose is the normal local runtime. Kind is a validation layer for Kubernetes behavior. Production Kubernetes manifests are prepared for EKS-style use with external dependencies, but the current workflow does not deploy them to EKS.

## Local Compose

Start the full local topology:

```powershell
docker compose up -d --build
```

Run the local validation script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-local.ps1
```

`validate-local.ps1` checks Docker and Compose, validates Compose config, starts containers, waits for `db`, `message-br`, `cache`, and `server` health, runs `smoke-local.ps1`, then runs Maven tests and Maven package.

`smoke-local.ps1` verifies:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/health/liveness`
- `http://localhost:8080/actuator/health/readiness`
- PostgreSQL with `pg_isready`
- RabbitMQ with `rabbitmq-diagnostics ping` and `check_port_connectivity`
- Redis with `redis-cli ping`

Stop the local stack:

```powershell
docker compose down
```

Use `docker compose down -v` only when you intentionally want to remove local named volumes.

## Build and Test Commands

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
docker build -t auronix-server:local .
```

On Unix-like shells, use `./mvnw` instead of `.\mvnw.cmd`.

## Testcontainers Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-testcontainers.ps1
```

The script requires Docker, runs only `FinancePostgresIntegrationTest`, verifies that the Surefire XML report exists, and fails if the test count is zero or if any `skipped`, `failures`, or `errors` value is non-zero.

## Full Local/Offline Pipeline

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-all.ps1
```

`validate-all.ps1` runs:

1. `git diff --check`.
2. Maven tests.
3. Maven package.
4. `docker compose config --quiet`.
5. Optional full Compose validation unless `-SkipCompose` is used.
6. Testcontainers validation.
7. Docker image build.
8. Kubernetes offline validation.
9. Terraform validation.
10. Optional Kind validation when `-IncludeKind` is supplied.

## Kubernetes

Offline validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

Kind validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

Useful Kubernetes checks when a context is intentionally selected:

```powershell
kubectl rollout status deployment/server -n auronix
kubectl rollout history deployment/server -n auronix
kubectl rollout undo deployment/server -n auronix
kubectl get pods -n auronix
kubectl logs -n auronix deployment/server
```

## Terraform and AWS Validation

Offline Terraform validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-terraform.ps1
```

Connected AWS validation:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

The AWS script first validates identity with `aws sts get-caller-identity`. If credentials are unavailable or expired, it exits before Terraform plan, EKS, kubeconfig, or kubectl connected operations.

## Operational Notes

- `/actuator/health/liveness` should reflect the running process.
- `/actuator/health/readiness` includes dependency readiness when probe groups are enabled.
- RabbitMQ redeliveries are expected; consumers using the shared wrapper deduplicate by `eventId`.
- Outbox rows that are `PENDING` or timed-out `PROCESSING` are retried by the scheduler.
- Redis Pub/Sub notifications are not durable; clients should use HTTP endpoints to recover persisted state after reconnecting.
- `terraform apply`, `terraform destroy`, and external `kubectl apply` are not part of local validation scripts.
