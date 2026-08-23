# Operacao

## Visao de Ambientes

```text
LOCAL DEVELOPMENT

Auronix
|-- Spring Boot
|-- PostgreSQL via Docker Compose
|-- RabbitMQ via Docker Compose
`-- Redis via Docker Compose
```

```text
LOCAL KUBERNETES VALIDATION

kind
`-- manifests Kubernetes validados em cluster descartavel
```

```text
PRODUCTION

AWS
|-- Terraform
|-- VPC
|-- EKS
|-- workloads Kubernetes
|-- PostgreSQL externo ao ciclo de vida dos Pods
|-- RabbitMQ externo ou servico gerenciado a definir
`-- Redis externo ou servico gerenciado a definir
```

As dependencias de infraestrutura para desenvolvimento local rodam via Docker Compose. Kubernetes local existe apenas para validacao operacional de manifests, probes, rollout, rollback e shutdown; ele nao substitui Docker Compose no fluxo normal de desenvolvimento.

## Ambiente Local

Pre-requisitos:

- Docker Desktop ou Docker Engine com Docker Compose.
- JDK 26.
- Maven Wrapper do projeto.

Subir o ambiente completo:

```powershell
docker compose config
docker compose up -d --build
docker compose ps
```

Executar validacao local completa com Compose:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-local.ps1
```

Executar smoke tests depois que o Compose estiver de pe:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-local.ps1
```

Parar o ambiente:

```powershell
docker compose down
```

Limpar volumes locais:

```powershell
docker compose down -v
```

Servicos locais:

| Servico | Porta | Validacao |
| --- | --- | --- |
| `server` | `8080` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` |
| `db` | `5432` | `pg_isready` dentro do container |
| `message-br` | `5672`, `15672` | `rabbitmq-diagnostics ping` e porta AMQP |
| `cache` | `6379` | `redis-cli ping` |

Logs uteis:

```powershell
docker compose logs -f server
docker compose logs -f db
docker compose logs -f message-br
docker compose logs -f cache
```

## Testes e Build

Testes Maven:

```powershell
.\mvnw.cmd test
```

Package:

```powershell
.\mvnw.cmd package -DskipTests
```

Testcontainers obrigatorio quando Docker estiver disponivel:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-testcontainers.ps1
```

Build Docker:

```powershell
docker build -t auronix-server:local .
```

Fluxo local/offline central:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-all.ps1
```

Para pular a subida completa do Compose quando ele ja foi validado:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-all.ps1 -SkipCompose
```

Para incluir validacao Kubernetes real em kind:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-all.ps1 -IncludeKind
```

## Kubernetes

Validacao offline, sem AWS e sem EKS:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

Validacao real em cluster local kind:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

O script de kind constroi a imagem local, carrega no cluster, aplica `k8s/overlays/local`, aguarda rollouts, valida health/readiness/liveness por port-forward, testa `kubectl rollout restart`, `kubectl rollout history`, `kubectl rollout undo` e remove um Pod para exercitar recuperacao.

## Terraform

Validacao offline:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-terraform.ps1
```

Validacao AWS segura:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

O fluxo AWS inicia por `aws sts get-caller-identity`. Se a identidade nao puder ser validada, nenhum comando de EKS, Kubernetes conectado ou Terraform plan e executado.

## Rollout e Rollback

Com contexto Kubernetes correto:

```powershell
kubectl rollout status deployment/server -n auronix
kubectl rollout history deployment/server -n auronix
kubectl rollout undo deployment/server -n auronix
kubectl get pods -n auronix
kubectl describe deployment server -n auronix
kubectl logs -n auronix deployment/server
```

Producao deve usar imagem imutavel por digest ou SHA publicado pela pipeline. A relacao operacional esperada e:

```text
commit Git
|
imagem Docker por SHA/digest
|
Deployment Kubernetes
|
revision Kubernetes
```

## Checklist Operacional

- `docker compose ps` mostra os quatro servicos healthy no ambiente local.
- `/actuator/health/liveness` responde sem depender de banco, RabbitMQ ou Redis.
- `/actuator/health/readiness` so aceita trafego quando a aplicacao esta pronta.
- `kubectl rollout status` conclui antes de promover mudancas.
- `kubectl get pods -n auronix` nao e suficiente sozinho; verifique logs, restart count, readiness, liveness e eventos.
- `terraform plan` deve ser revisado antes de qualquer apply manual.
- `terraform apply` e `terraform destroy` nao fazem parte das validacoes locais automatizadas.
