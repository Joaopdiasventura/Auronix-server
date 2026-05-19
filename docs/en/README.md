# Auronix Server Documentation

This documentation describes the Auronix backend using the files present in the repository. It covers application behavior, infrastructure, deployment, configuration, automated tests, CI/CD, operations, and security.

## Topics

- [Architecture](architecture/README.md)
- [Application](application/README.md)
- [Infrastructure](infrastructure/README.md)
- [Kubernetes](kubernetes/README.md)
- [Terraform](terraform/README.md)
- [Configuration](configuration/README.md)
- [Testing](testing/README.md)
- [CI/CD](ci-cd/README.md)
- [Operations](operations/README.md)
- [Security](security/README.md)

## Project Snapshot

Auronix is implemented as a Java 26 Spring Boot 4.0.6 API. It uses PostgreSQL for persistence, RabbitMQ for asynchronous domain events, Redis for SSE connection metadata, Docker Compose for local dependencies, Kubernetes manifests for cluster deployment, and Terraform for AWS EKS and Kubernetes resource provisioning.
