# Auronix Kubernetes

Manifests locais para executar o backend Auronix com PostgreSQL, RabbitMQ, Redis e autoscaling do servidor.

## Aplicar

Para publicar uma nova imagem do backend:

```sh
docker build -t jpplay/auditex-server:latest .
docker push jpplay/auditex-server:latest
```

```sh
kubectl apply -f k8s/
```

Se o `kubectl` tentar acessar `http://localhost:8080/openapi/v2` ou disser que `current-context is not set`, o cluster local ainda nao esta configurado para o `kubectl`.

No Docker Desktop, habilite Kubernetes em Settings > Kubernetes > Enable Kubernetes, aguarde o cluster iniciar e confirme:

```sh
kubectl config get-contexts
kubectl config use-context docker-desktop
kubectl cluster-info
```

Depois aplique novamente:

```sh
kubectl apply -f k8s/
```

## Verificar

```sh
kubectl get pods -n auronix
kubectl get svc -n auronix
kubectl get hpa -n auronix
kubectl logs -n auronix deploy/server -f
```

## Acessar o backend localmente

O Service do backend e interno (`ClusterIP`). Use port-forward:

```sh
kubectl port-forward -n auronix svc/auronix-server 8080:8080
```

Depois acesse:

```text
http://localhost:8080/actuator/health
```

## Autoscaling

O HPA escala o Deployment `server` de 1 ate 3 pods usando CPU media alvo de 70%.

O cluster precisa do Metrics Server instalado e funcionando para que o HPA calcule uso de CPU.

Minikube:

```sh
minikube addons enable metrics-server
```

Docker Desktop Kubernetes pode exigir instalacao manual do Metrics Server.

## DNS interno

O backend usa os Services internos do Kubernetes:

- PostgreSQL: `postgres:5432`
- RabbitMQ AMQP: `rabbitmq:5672`
- RabbitMQ Management local: `rabbitmq:15672`
- Redis: `redis:6379`
