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

`k8s/base` defines the shared Kustomize resources: namespace, Secret shape, ConfigMap, PostgreSQL, RabbitMQ, Redis, metrics-server, server Deployment/Service, and HPA. The overlays patch that base for local, staging, and production. The flat `k8s/*.yaml` files remain because `infra/terraform/app` reads that set directly.

## Base Workload

The server deployment has two replicas by default, `terminationGracePeriodSeconds: 45`, a `preStop` sleep of 10 seconds, requests of `250m` CPU and `512Mi`, limits of `750m` CPU and `1Gi`, and a `LoadBalancer` service mapping port `80` to container port `8080`.

The image is pinned by digest in base and production placeholders:

```text
jpplay/auronix-server@sha256:0000000000000000000000000000000000000000000000000000000000000000
```

This placeholder must be replaced by a real immutable digest before use.

## Probes

The server uses separate probes:

- `startupProbe`: `GET /actuator/health/liveness`, long failure threshold for slow startup.
- `livenessProbe`: `GET /actuator/health/liveness`, restarts the container when the process is unhealthy.
- `readinessProbe`: `GET /actuator/health/readiness`, removes the Pod from service endpoints when readiness dependencies are unavailable.

The application config includes readiness indicators `readinessState,db,rabbit,redis`. Liveness is intentionally narrower than readiness.

## Overlays

### Local

`k8s/overlays/local` is for local Kubernetes validation with Kind. It keeps PostgreSQL, RabbitMQ, and Redis in the cluster, sets the server image to `auronix-server:kind-v1`, keeps `imagePullPolicy: IfNotPresent`, changes the service to `ClusterIP`, sets server replicas to 1, lowers HPA minimum to 1, and enables local JPA update/SQL logging.

### Staging

`k8s/overlays/staging` currently only reduces the server deployment to 1 replica and sets HPA bounds to 1-3. It otherwise inherits the base topology, including in-cluster PostgreSQL, RabbitMQ, and Redis.

### Production

`k8s/overlays/production` removes PostgreSQL, RabbitMQ, and Redis workloads from the base and sets external endpoint placeholders:

- `DATABASE_URL=jdbc:postgresql://REPLACE_WITH_RDS_ENDPOINT:5432/auronix`
- `RABBITMQ_URL=amqp://REPLACE_WITH_RABBITMQ_ENDPOINT:5672/`
- `REDIS_URL=redis://REPLACE_WITH_REDIS_ENDPOINT:6379`

The overlay deletes each dependency resource with separate `$patch: delete` files for the StatefulSet/Deployment and Service:

- `delete-postgres-statefulset.yaml`
- `delete-postgres-service.yaml`
- `delete-rabbitmq-deployment.yaml`
- `delete-rabbitmq-service.yaml`
- `delete-redis-deployment.yaml`
- `delete-redis-service.yaml`

That split is the current shape after the production Kustomize delete fix. Production keeps two server replicas and the digest-pinned image placeholder. RDS/Aurora, Amazon MQ, and ElastiCache are natural external endpoint candidates, but they are not currently provisioned in this repository.

## Offline Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-offline.ps1
```

The script renders `local`, `staging`, and `production` with `kubectl kustomize` into `target/k8s/*.yaml`, then validates the rendered manifests with `kubeconform`. It uses local tools or a kubeconform Docker image. It does not require AWS credentials or an EKS cluster.

## Kind Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-k8s-kind.ps1
```

The Kind script requires Docker, `kubectl`, and `kind`. It builds two local images, creates or reuses a Kind cluster, loads both images, applies `k8s/overlays/local`, waits for PostgreSQL/RabbitMQ/Redis/server rollouts, checks pods, verifies probes, validates dependency connectivity, and checks `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` through port-forward.

It also exercises pod deletion/recreation, Spring graceful shutdown log observation, rolling update to the second image, rollout history, rollback/undo, readiness failure when PostgreSQL is scaled to zero, and scaling the server to three replicas. This tests real workload behavior in a disposable local Kubernetes cluster, not just YAML validity.

## Connected AWS Validation

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

This script is the connected validation path. It first runs `aws sts get-caller-identity`; if credentials are unavailable or expired, it prints a skip message and exits with code 2 before EKS or Kubernetes actions. With valid credentials, it prints account, ARN, region, and cluster, runs Terraform `init`, shows the workspace, creates a plan file, describes the EKS cluster, updates kubeconfig, checks `kubectl cluster-info`, lists nodes and namespaces, runs server-side dry-run for `k8s/overlays/production`, and executes `kubectl diff`.

Do not confuse this with offline validation: it requires AWS credentials and an existing target cluster.
