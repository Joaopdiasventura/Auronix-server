# Kubernetes

## Layout

```text
k8s/
|-- base/
|-- overlays/
|   |-- local/
|   |-- staging/
|   `-- production/
`-- *.yaml
```

`k8s/base` define os recursos Kustomize compartilhados: namespace, formato de Secret, ConfigMap, PostgreSQL, RabbitMQ, Redis, metrics-server, Deployment/Service do server e HPA. Os overlays ajustam essa base para local, staging e produção. Os manifests planos `k8s/*.yaml` permanecem porque `infra/terraform/app` lê esse conjunto diretamente.

## Workload Base

O deployment do server tem duas réplicas por padrão, `terminationGracePeriodSeconds: 45`, `preStop` com sleep de 10 segundos, requests de `250m` CPU e `512Mi`, limits de `750m` CPU e `1Gi`, e Service `LoadBalancer` mapeando a porta `80` para a porta `8080` do container.

A imagem é fixada por digest nos placeholders de base e produção:

```text
jpplay/auronix-server@sha256:0000000000000000000000000000000000000000000000000000000000000000
```

Esse placeholder deve ser substituído por um digest imutável real antes de uso.

## Probes

O server usa probes separados:

- `startupProbe`: `GET /actuator/health/liveness`, com threshold longo para inicialização lenta.
- `livenessProbe`: `GET /actuator/health/liveness`, reinicia o container quando o processo fica unhealthy.
- `readinessProbe`: `GET /actuator/health/readiness`, remove o Pod dos endpoints de serviço quando dependências de readiness estão indisponíveis.

A configuração da aplicação inclui indicadores de readiness `readinessState,db,rabbit,redis`. Liveness é intencionalmente mais estreito que readiness.

## Overlays

### Local

`k8s/overlays/local` serve para validação Kubernetes local com Kind. Ele mantém PostgreSQL, RabbitMQ e Redis no cluster, define a imagem do server como `auronix-server:kind-v1`, mantém `imagePullPolicy: IfNotPresent`, muda o Service para `ClusterIP`, configura uma réplica do server, reduz o mínimo do HPA para 1 e habilita update de schema JPA e log SQL locais.

### Staging

`k8s/overlays/staging` atualmente apenas reduz o deployment do server para uma réplica e define limites do HPA entre 1 e 3. No restante, herda a topologia da base, incluindo PostgreSQL, RabbitMQ e Redis dentro do cluster.

### Produção

`k8s/overlays/production` remove workloads PostgreSQL, RabbitMQ e Redis da base e define placeholders de endpoints externos:

- `DATABASE_URL=jdbc:postgresql://REPLACE_WITH_RDS_ENDPOINT:5432/auronix`
- `RABBITMQ_URL=amqp://REPLACE_WITH_RABBITMQ_ENDPOINT:5672/`
- `REDIS_URL=redis://REPLACE_WITH_REDIS_ENDPOINT:6379`

O overlay remove cada recurso de dependência com arquivos `$patch: delete` separados para StatefulSet/Deployment e Service:

- `delete-postgres-statefulset.yaml`
- `delete-postgres-service.yaml`
- `delete-rabbitmq-deployment.yaml`
- `delete-rabbitmq-service.yaml`
- `delete-redis-deployment.yaml`
- `delete-redis-service.yaml`

Essa separação é o formato atual depois da correção do delete em Kustomize de produção. Produção mantém duas réplicas do server e o placeholder de imagem fixada por digest. RDS/Aurora, Amazon MQ e ElastiCache são candidatos naturais para endpoints externos, mas não são provisionados atualmente neste repositório.

## Validação Offline

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

O script renderiza `local`, `staging` e `production` com `kubectl kustomize` em `target/k8s/*.yaml` e valida os manifests renderizados com `kubeconform`. Ele usa ferramentas locais ou uma imagem Docker do kubeconform. Não exige credenciais AWS nem cluster EKS.

## Validação Kind

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

O script Kind exige Docker, `kubectl` e `kind`. Ele constrói duas imagens locais, cria ou reutiliza um cluster Kind, carrega as duas imagens, aplica `k8s/overlays/local`, aguarda rollouts de PostgreSQL/RabbitMQ/Redis/server, checa Pods, verifica probes, valida conectividade das dependências e checa `/actuator/health`, `/actuator/health/liveness` e `/actuator/health/readiness` por port-forward.

Ele também exercita deleção/recriação de Pod, observação de log de graceful shutdown do Spring, rolling update para a segunda imagem, rollout history, rollback/undo, falha de readiness quando PostgreSQL é escalado para zero e escala do server para três réplicas. Isso testa comportamento real dos workloads em um cluster Kubernetes local descartável, não apenas validade de YAML.

## Validação AWS Conectada

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

Esse script é o caminho de validação conectada. Ele primeiro executa `aws sts get-caller-identity`; se as credenciais estiverem indisponíveis ou expiradas, imprime uma mensagem de skip e sai com código 2 antes de ações EKS ou Kubernetes. Com credenciais válidas, imprime account, ARN, região e cluster, executa Terraform `init`, mostra o workspace, cria um arquivo de plan, descreve o cluster EKS, atualiza kubeconfig, checa `kubectl cluster-info`, lista nodes e namespaces, executa dry-run server-side para `k8s/overlays/production` e executa `kubectl diff`.

Não confunda isso com validação offline: ela exige credenciais AWS e um cluster de destino existente.
