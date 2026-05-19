# Operacao

## Execucao Local

O Auronix pode ser executado localmente de duas formas: como stack completa via Docker Compose ou como processo Spring Boot standalone conectado a dependencias locais.

### Opcao de Desenvolvimento Local 1: Docker Compose

Fluxo recomendado para validar o ambiente local completo, incluindo o container da API e todas as dependencias de runtime.

Pre-requisitos:

- Docker com suporte a Docker Compose.
- Acesso de rede para baixar imagens base e dependencias Maven durante o build da imagem.

Arquivos Compose identificados no projeto:

| Arquivo | Finalidade |
| --- | --- |
| `compose.yaml` | Constroi a imagem do backend e inicia PostgreSQL, RabbitMQ e o servico `cache`, baseado em Redis, para desenvolvimento local. |

Subir o ambiente:

```bash
docker compose up --build
```

Executar em background:

```bash
docker compose up --build -d
```

Parar o ambiente:

```bash
docker compose down
```

Servicos iniciados pelo Compose:

| Servico | Imagem ou origem | Portas expostas | Papel |
| --- | --- | --- | --- |
| `server` | Construido pelo `Dockerfile` local | `8080:8080` | API Spring Boot |
| `db` | `postgres:17-alpine` | `5432:5432` | Banco PostgreSQL |
| `message-br` | `rabbitmq:4-management` | `5672:5672`, `15672:15672` | Broker RabbitMQ e UI de gestao |
| `cache` | `redis:8-alpine` | `6379:6379` | Instancia Redis para metadados SSE |

O arquivo Compose fornece o ambiente necessario para o container da aplicacao, incluindo URL do banco, credenciais de banco, URL do RabbitMQ, URL do Redis e flags JPA. Na rede do Compose, a API usa `REDIS_URL=redis://cache:6379` para acessar o servico de cache baseado em Redis. Os valores sensiveis nesse arquivo local sao orientados a desenvolvimento; use gestao de segredos especifica do ambiente para ambientes compartilhados ou similares a producao.

Validar a API:

```bash
curl http://localhost:8080/actuator/health
```

Logs uteis:

```bash
docker compose logs -f server
docker compose logs -f db
docker compose logs -f message-br
docker compose logs -f cache
```

Observacao operacional: `db`, `message-br` e `cache` incluem health checks. Mantenha nomes de servicos Compose e URLs da aplicacao alinhados ao ajustar nomes de dependencias locais.

### Opcao de Desenvolvimento Local 2: Spring Boot Standalone

Fluxo recomendado para iterar na aplicacao Java mantendo as dependencias em containers locais ou outro runtime local.

Pre-requisitos:

- JDK 26.
- Maven Wrapper do projeto: `mvnw` ou `mvnw.cmd`.
- PostgreSQL, RabbitMQ e Redis em execucao antes da aplicacao iniciar.

Subir apenas as dependencias externas com o arquivo Compose existente:

```bash
docker compose up -d db message-br cache
```

A configuracao default da aplicacao espera endpoints locais compativeis com esses servicos do Compose:

| Variavel | Comportamento local default |
| --- | --- |
| `PORT` | Executa a API em `8080` quando nao sobrescrita |
| `DATABASE_URL` | PostgreSQL em localhost na porta `5432` |
| `DATABASE_USERNAME` | Usuario de banco de desenvolvimento das configuracoes locais |
| `DATABASE_PASSWORD` | Senha de banco de desenvolvimento das configuracoes locais |
| `RABBITMQ_URL` | RabbitMQ em localhost na porta `5672` |
| `REDIS_URL` | Redis em localhost na porta `6379` para execucao standalone, ou `redis://cache:6379` dentro da rede do Compose |
| `JWT_SECRET` | Segredo de assinatura JWT; defina um valor especifico do ambiente fora de uso apenas local |
| `CLIENT_URLS` | Origens CORS, com default para a origem frontend local configurada na aplicacao |

Executar a aplicacao:

```bash
./mvnw spring-boot:run
```

No Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Executar testes:

```bash
./mvnw test
```

No Windows:

```powershell
.\mvnw.cmd test
```

Validar a API:

```bash
curl http://localhost:8080/actuator/health
```

Observacao importante: com Spring Boot standalone, o processo Java roda diretamente no host enquanto PostgreSQL, RabbitMQ e Redis podem rodar separadamente. Com Docker Compose, a API e as dependencias rodam juntas como containers usando os nomes de rede definidos em `compose.yaml`, incluindo `cache` para Redis.

## Build e Testes

```bash
./mvnw test
./mvnw package
docker build -t auronix-server .
```

## Deploy Kubernetes

Os manifests podem ser aplicados diretamente quando um contexto de cluster estiver configurado:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/
```

O service da API e `auronix-server` no namespace `auronix`.

```bash
kubectl get pods -n auronix
kubectl get svc -n auronix
kubectl logs -n auronix deployment/server
```

## Deploy Terraform

Provisione primeiro a infraestrutura do cluster e depois a stack da aplicacao. Revise cada plan antes de aplicar.

```bash
cd infra/terraform/cluster
terraform init
terraform plan
terraform apply
```

Use o comando de output do cluster para configurar o kubeconfig, depois:

```bash
cd infra/terraform/app
terraform init
terraform plan
terraform apply
```

## Verificacoes de Troubleshooting

- `/actuator/health` deve retornar informacoes de saude quando a API estiver acessivel.
- Verifique readiness e liveness probes Kubernetes para `server`, `postgres`, `rabbitmq` e `redis`.
- Confirme se URLs de RabbitMQ e Redis correspondem ao ambiente de runtime.
- Confirme se as origens CORS configuradas incluem a origem da aplicacao cliente.
- Para processamento de transferencias, inspecione filas RabbitMQ e logs da aplicacao para atividade dos consumers.
