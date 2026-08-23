# CI/CD

## Workflow

O repositorio contem um workflow GitHub Actions em `.github/workflows/main.yml`, chamado `CI/CD`.

## Gatilhos e Permissoes

O workflow roda em push para todos os branches. Ele concede leitura de conteudo do repositorio e escrita em packages. A concorrencia e habilitada por workflow e referencia Git, cancelando execucoes em andamento para a mesma ref.

## Job Validate

O job `validate` roda em `ubuntu-latest` e executa:

- Checkout do repositorio.
- Configuracao do Java com Temurin 26 e cache Maven.
- Permissao de execucao para o Maven Wrapper.
- Resolucao de dependencias com `./mvnw -B dependency:go-offline`.
- Testes com `./mvnw -B test`.
- Build de package com `./mvnw -B package -DskipTests`.

## Job Publish

O job `publish` depende de `validate` e:

- Faz checkout do repositorio.
- Configura Docker Buildx.
- Faz login no Docker Hub usando secrets do repositorio para usuario e senha.
- Gera metadados Docker para a imagem `jpplay/auronix-server`.
- Publica uma tag SHA longa em pushes.
- Publica `latest` apenas quando o branch se chama `main`.
- Expoe o digest da imagem como output do job.

Consideracao operacional: o workflow publica imagens em pushes apos validacao bem-sucedida. Politicas de branch e registry devem estar alinhadas com o processo de release pretendido.
