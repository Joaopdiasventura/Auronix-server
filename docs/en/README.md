# Auronix Server Documentation

This documentation describes the current Auronix backend from the repository implementation. The code, manifests, scripts, Terraform stacks, tests, and GitHub Actions workflow are the source of truth.

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

Auronix is a Java 26 Spring Boot 4.0.6 API backed by PostgreSQL, RabbitMQ, and Redis. PostgreSQL is the source of truth for financial state, the transactional outbox, and processed-event idempotency records. RabbitMQ carries asynchronous domain events. Redis is used for SSE connection metadata and Pub/Sub distribution between replicas.

Local development uses Docker Compose. Kubernetes is organized with Kustomize base and overlays for local, staging, and production validation. Terraform is split into an AWS cluster stack and a Kubernetes app stack. Production Kubernetes manifests expect external PostgreSQL, RabbitMQ, and Redis endpoints; those managed services are not provisioned by the current Terraform.
