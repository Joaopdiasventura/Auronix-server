# CI/CD

## Workflow

The repository contains one GitHub Actions workflow at `.github/workflows/main.yml`, named `CI/CD`.

## Triggers and Permissions

The workflow runs on push to all branches. It grants read access to repository contents and write access to packages. Concurrency is enabled per workflow and Git reference, cancelling in-progress runs for the same ref.

## Validate Job

The `validate` job runs on `ubuntu-latest` and performs:

- Repository checkout.
- Java setup with Temurin 26 and Maven cache.
- Maven Wrapper executable permission.
- Dependency resolution with `./mvnw -B dependency:go-offline`.
- Tests with `./mvnw -B test`.
- Package build with `./mvnw -B package -DskipTests`.

## Publish Job

The `publish` job depends on `validate` and:

- Checks out the repository.
- Sets up Docker Buildx.
- Logs in to Docker Hub using repository secrets for username and password.
- Generates Docker metadata for image `jpplay/auditex-server`.
- Publishes a long SHA tag on pushes.
- Publishes `latest` only when the branch name is `main`.
- Exposes the image digest as a job output.

Operational consideration: the workflow publishes images on pushes after validation succeeds. Branch and registry policies should be aligned with the intended release process.
