# Operations

## Local Execution

Auronix can be executed locally in two ways: as a full Docker Compose stack or as a standalone Spring Boot process connected to local dependencies.

### Local Development Option 1: Docker Compose

Recommended workflow when validating the full local environment, including the API container and all runtime dependencies.

Prerequisites:

- Docker with Docker Compose support.
- Network access to download base images and Maven dependencies during the image build.

Compose files identified in the project:

| File | Purpose |
| --- | --- |
| `compose.yaml` | Builds the backend image and starts PostgreSQL, RabbitMQ, and the Redis-backed `cache` service for local development. |

Start the environment:

```bash
docker compose up --build
```

Run it in the background:

```bash
docker compose up --build -d
```

Stop the environment:

```bash
docker compose down
```

Services started by Compose:

| Service | Image or source | Exposed ports | Role |
| --- | --- | --- | --- |
| `server` | Built from the local `Dockerfile` | `8080:8080` | Spring Boot API |
| `db` | `postgres:17-alpine` | `5432:5432` | PostgreSQL database |
| `message-br` | `rabbitmq:4-management` | `5672:5672`, `15672:15672` | RabbitMQ broker and management UI |
| `cache` | `redis:8-alpine` | `6379:6379` | Redis instance for SSE metadata |

The Compose file provides the application environment needed by the container, including database URL, database credentials, RabbitMQ URL, Redis URL, and JPA flags. In the Compose network, the API uses `REDIS_URL=redis://cache:6379` to reach the Redis-backed cache service. Sensitive values are development-oriented in this local file; use environment-specific secret management for shared or production-like environments.

Validate the API:

```bash
curl http://localhost:8080/actuator/health
```

Useful logs:

```bash
docker compose logs -f server
docker compose logs -f db
docker compose logs -f message-br
docker compose logs -f cache
```

Operational note: `db`, `message-br`, and `cache` include health checks. Keep Compose service names and application URLs aligned when adjusting local dependency names.

### Local Development Option 2: Standalone Spring Boot

Recommended workflow when iterating on the Java application while keeping dependencies in local containers or another local runtime.

Prerequisites:

- JDK 26.
- The project Maven Wrapper: `mvnw` or `mvnw.cmd`.
- PostgreSQL, RabbitMQ, and Redis running before the application starts.

Start only the external dependencies with the existing Compose file:

```bash
docker compose up -d db message-br cache
```

The default application configuration expects local dependency endpoints compatible with these Compose services:

| Variable | Default local behavior |
| --- | --- |
| `PORT` | Runs the API on `8080` when not overridden |
| `DATABASE_URL` | PostgreSQL on localhost port `5432` |
| `DATABASE_USERNAME` | Development database user from local settings |
| `DATABASE_PASSWORD` | Development database password from local settings |
| `RABBITMQ_URL` | RabbitMQ on localhost port `5672` |
| `REDIS_URL` | Redis on localhost port `6379` for standalone execution, or `redis://cache:6379` inside the Compose network |
| `JWT_SECRET` | JWT signing secret; set an environment-specific value outside local-only use |
| `CLIENT_URLS` | CORS origins, defaulting to the local frontend origin configured in the application |

Run the application:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Run tests:

```bash
./mvnw test
```

On Windows:

```powershell
.\mvnw.cmd test
```

Validate the API:

```bash
curl http://localhost:8080/actuator/health
```

Important note: with standalone Spring Boot, the Java process runs directly on the host while PostgreSQL, RabbitMQ, and Redis can run separately. With Docker Compose, the API and dependencies run together as containers using the network names defined in `compose.yaml`, including `cache` for Redis.

## Build and Test

```bash
./mvnw test
./mvnw package
docker build -t auronix-server .
```

## Kubernetes Deployment

The manifests can be applied directly when a cluster context is configured:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/
```

The API service is `auronix-server` in namespace `auronix`.

```bash
kubectl get pods -n auronix
kubectl get svc -n auronix
kubectl logs -n auronix deployment/server
```

## Terraform Deployment

Provision cluster infrastructure first, then the app stack. Review each plan before applying.

```bash
cd infra/terraform/cluster
terraform init
terraform plan
terraform apply
```

Use the cluster output command to configure kubeconfig, then:

```bash
cd infra/terraform/app
terraform init
terraform plan
terraform apply
```

## Troubleshooting Checks

- `/actuator/health` should return health information when the API is reachable.
- Check Kubernetes readiness and liveness probe results for `server`, `postgres`, `rabbitmq`, and `redis`.
- Confirm RabbitMQ and Redis URLs match the runtime environment.
- Confirm the configured CORS origins include the client application origin.
- For transfer processing, inspect RabbitMQ queues and application logs for consumer activity.
