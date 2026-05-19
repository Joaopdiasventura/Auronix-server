# Documentacao do Auronix Server

Esta documentacao descreve o backend Auronix com base nos arquivos presentes no repositorio. Ela cobre comportamento da aplicacao, infraestrutura, deploy, configuracao, testes automatizados, CI/CD, operacao e seguranca.

## Topicos

- [Arquitetura](arquitetura/README.md)
- [Aplicacao](aplicacao/README.md)
- [Infraestrutura](infraestrutura/README.md)
- [Kubernetes](kubernetes/README.md)
- [Terraform](terraform/README.md)
- [Configuracao](configuracao/README.md)
- [Testes](testes/README.md)
- [CI/CD](ci-cd/README.md)
- [Operacao](operacao/README.md)
- [Seguranca](seguranca/README.md)

## Resumo do Projeto

Auronix e implementado como uma API Java 26 com Spring Boot 4.0.6. O projeto usa PostgreSQL para persistencia, RabbitMQ para eventos de dominio assincronos, Redis para metadados de conexoes SSE, Docker Compose para dependencias locais, manifests Kubernetes para deploy em cluster e Terraform para provisionamento de AWS EKS e recursos Kubernetes.
