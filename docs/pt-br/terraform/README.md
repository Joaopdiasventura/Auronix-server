# Terraform

## Organizacao

- `infra/terraform/cluster`: VPC e EKS na AWS.
- `infra/terraform/app`: manifests Kubernetes da aplicacao em um cluster existente.

## Validacao Offline

Nao exige AWS:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-terraform.ps1
```

O script executa para `cluster` e `app`:

```text
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
trivy config
```

Esse fluxo valida sintaxe, providers, modulos e misconfigurations sem acessar state remoto e sem executar `apply`.

## Validacao AWS

Exige credenciais validas:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\validate-aws.ps1
```

Sequencia:

```text
aws sts get-caller-identity
|
mostrar account, ARN, regiao e cluster
|
terraform init
|
terraform workspace show
|
terraform plan
|
aws eks describe-cluster
|
aws eks update-kubeconfig
|
kubectl cluster-info
|
kubectl get nodes
|
kubectl get namespaces
|
kubectl apply --dry-run=server
|
kubectl diff
```

Se `aws sts get-caller-identity` falhar, o script para imediatamente. Isso evita diagnosticar credencial expirada como erro de Kubernetes.

## Apply

`terraform apply` nao faz parte das validacoes automaticas locais. Use apply somente manualmente, apos revisar account, regiao, backend, workspace, plan e impacto esperado.

`terraform destroy` tambem nao faz parte dos scripts de validacao.

## Providers

As stacks usam constraints versionadas:

- Terraform `>= 1.6.0`.
- AWS provider `~> 5.0`.
- Kubernetes provider `~> 2.33`.
- Modulos VPC e EKS com major version fixado por constraint.

Nao use provider `latest` em infraestrutura critica.

## Producao

A stack `cluster` provisiona rede e EKS. A stack `app` aplica namespace, secrets, configmap, metrics-server, deployment/service da API e HPA.

PostgreSQL, RabbitMQ e Redis de producao sao dependencias externas configuradas por endpoint. O Terraform atual nao provisiona RDS/Aurora, Amazon MQ ou ElastiCache.
