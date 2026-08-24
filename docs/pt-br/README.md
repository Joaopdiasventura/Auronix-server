# Documentação do Auronix Server

Esta documentação descreve o backend Auronix atual a partir da implementação do repositório. Código, manifests, scripts, stacks Terraform, testes e workflow GitHub Actions são a fonte de verdade.

## Tópicos

- [Arquitetura](arquitetura/README.md)
- [Aplicação](aplicacao/README.md)
- [Infraestrutura](infraestrutura/README.md)
- [Kubernetes](kubernetes/README.md)
- [Terraform](terraform/README.md)
- [Configuração](configuracao/README.md)
- [Testes](testes/README.md)
- [CI/CD](ci-cd/README.md)
- [Operação](operacao/README.md)
- [Segurança](seguranca/README.md)

## Resumo do Projeto

Auronix é uma API Java 26 com Spring Boot 4.0.6 apoiada por PostgreSQL, RabbitMQ e Redis. O PostgreSQL é a fonte de verdade para estado financeiro, outbox transacional e registros de idempotência de eventos processados. RabbitMQ transporta eventos de domínio assíncronos. Redis é usado para metadata de conexões SSE e distribuição Pub/Sub entre réplicas.

Desenvolvimento local usa Docker Compose. Kubernetes é organizado com base Kustomize e overlays para validação local, staging e produção. Terraform é dividido em uma stack AWS de cluster e uma stack Kubernetes de aplicação. Os manifests Kubernetes de produção esperam endpoints externos de PostgreSQL, RabbitMQ e Redis; esses serviços gerenciados não são provisionados pelo Terraform atual.
