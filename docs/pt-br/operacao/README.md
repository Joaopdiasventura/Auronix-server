# Operação

## Modelo de Ambientes

```text
Desenvolvimento local:
Docker Compose -> server, db, message-br, cache

Validação Kubernetes local:
Kind -> overlay local Kustomize com dependências in-cluster

Manifests Kubernetes de produção:
Workloads Auronix -> endpoints externos PostgreSQL, RabbitMQ, Redis

Deploy atual do GitHub Actions em main:
runner Docker self-hosted -> um container auronix-server por digest publicado
```

Docker Compose é o runtime local normal. Kind é uma camada de validação de comportamento Kubernetes. Os manifests Kubernetes de produção estão preparados para uso em estilo EKS com dependências externas, mas o workflow atual não faz deploy deles em EKS.

## Compose Local

Subir a topologia local completa:

```powershell
docker compose up -d --build
```

Executar a validação local:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-local.ps1
```

`validate-local.ps1` verifica Docker e Compose, valida a configuração Compose, inicia containers, aguarda health de `db`, `message-br`, `cache` e `server`, executa `smoke-local.ps1`, depois roda testes Maven e package Maven.

`smoke-local.ps1` verifica:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/health/liveness`
- `http://localhost:8080/actuator/health/readiness`
- PostgreSQL com `pg_isready`
- RabbitMQ com `rabbitmq-diagnostics ping` e `check_port_connectivity`
- Redis com `redis-cli ping`

Parar a stack local:

```powershell
docker compose down
```

Use `docker compose down -v` somente quando quiser remover os volumes nomeados locais.

## Comandos de Build e Teste

```powershell
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
docker build -t auronix-server:local .
```

Em shells Unix-like, use `./mvnw` em vez de `.\mvnw.cmd`.

## Validação Testcontainers

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-testcontainers.ps1
```

O script exige Docker, executa apenas `FinancePostgresIntegrationTest`, verifica que o relatório XML do Surefire existe e falha se a contagem de testes for zero ou se qualquer valor de `skipped`, `failures` ou `errors` for diferente de zero.

## Pipeline Local/Offline Completo

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-all.ps1
```

`validate-all.ps1` executa:

1. `git diff --check`.
2. Testes Maven.
3. Package Maven.
4. `docker compose config --quiet`.
5. Validação completa do Compose, exceto quando `-SkipCompose` é usado.
6. Validação Testcontainers.
7. Build da imagem Docker.
8. Validação Kubernetes offline.
9. Validação Terraform.
10. Validação Kind opcional quando `-IncludeKind` é informado.

## Kubernetes

Validação offline:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

Validação Kind:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

Comandos úteis quando um contexto Kubernetes foi selecionado intencionalmente:

```powershell
kubectl rollout status deployment/server -n auronix
kubectl rollout history deployment/server -n auronix
kubectl rollout undo deployment/server -n auronix
kubectl get pods -n auronix
kubectl logs -n auronix deployment/server
```

## Terraform e Validação AWS

Validação Terraform offline:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-terraform.ps1
```

Validação AWS conectada:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

O script AWS primeiro valida identidade com `aws sts get-caller-identity`. Se as credenciais estiverem indisponíveis ou expiradas, ele sai antes de Terraform plan, EKS, kubeconfig ou operacoes conectadas de kubectl.

## Notas Operacionais

- `/actuator/health/liveness` deve refletir o processo em execução.
- `/actuator/health/readiness` inclui readiness das dependências quando grupos de probe estão habilitados.
- Redeliveries RabbitMQ são esperadas; consumers que usam o wrapper compartilhado deduplicam por `eventId`.
- Linhas de outbox em `PENDING` ou `PROCESSING` expirado são tentadas novamente pelo scheduler.
- Notificações Redis Pub/Sub não são duráveis; clientes devem usar endpoints HTTP para recuperar estado persistido após reconexão.
- `terraform apply`, `terraform destroy` e `kubectl apply` externo não fazem parte dos scripts de validação local.
