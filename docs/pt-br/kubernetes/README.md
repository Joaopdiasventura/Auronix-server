# Kubernetes

## Estrutura

```text
k8s/
|-- base/
`-- overlays/
    |-- local/
    |-- staging/
    `-- production/
```

Os manifests planos em `k8s/*.yaml` continuam presentes para compatibilidade com a stack Terraform da aplicacao. A estrutura Kustomize e usada para renderizar e validar ambientes sem depender de AWS.

## Ambientes

`local` serve apenas para validacao Kubernetes. Ele usa `server-server:latest` com `imagePullPolicy: Never` e inclui Postgres, RabbitMQ e Redis dentro do cluster descartavel.

`staging` preserva a topologia de base com limites menores de replicas.

`production` remove Postgres, RabbitMQ e Redis in-cluster e aponta a aplicacao para endpoints externos. A imagem de producao nao deve usar `latest`; use tag por SHA ou digest imutavel.

## Probes

O deployment da API usa:

- `startupProbe` em `/actuator/health/liveness`.
- `livenessProbe` em `/actuator/health/liveness`.
- `readinessProbe` em `/actuator/health/readiness`.

Liveness deve indicar se o processo esta vivo. Readiness deve impedir trafego antes de a instancia aceitar requisicoes. Startup probe protege inicializacoes mais lentas para nao acionar liveness cedo demais.

## Validacao Offline

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

O script renderiza `local`, `staging` e `production` com `kubectl kustomize` em `target/k8s` e valida os YAMLs com `kubeconform`. Ele nao usa `aws eks update-kubeconfig`, nao acessa EKS e nao exige credenciais AWS.

## Validacao Local Real

Ferramenta escolhida: kind.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

Fluxo:

```text
docker build
|
kind create cluster
|
kind load docker-image
|
kubectl apply -k k8s/overlays/local
|
rollout status
|
port-forward
|
health/readiness/liveness
|
rollout restart
|
rollout history
|
rollout undo
|
delete pod
|
rollout status
```

Use `-Destroy` para destruir o cluster ao final.

## Validacao EKS

Com credenciais validas:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

O script valida account, ARN, regiao, cluster e workspace antes de comandos conectados. Depois executa `terraform plan`, `aws eks describe-cluster`, `aws eks update-kubeconfig`, `kubectl cluster-info`, `kubectl get nodes`, `kubectl get namespaces`, `kubectl apply --dry-run=server` e `kubectl diff`.

`kubectl diff` retornar diferencas nao e falha por si so. Falha e erro de acesso, schema, admission ou comando.

## Rollback

```powershell
kubectl rollout history deployment/server -n auronix
kubectl rollout undo deployment/server -n auronix
kubectl rollout status deployment/server -n auronix
```

Rollback depende de imagem anterior ainda disponivel no registry. Por isso producao deve usar tag por SHA ou digest.
