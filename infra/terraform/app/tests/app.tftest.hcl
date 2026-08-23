mock_provider "kubernetes" {}

run "production_app_uses_external_dependencies" {
  command = plan

  assert {
    condition     = !contains(local.kubernetes_manifest_files, "postgres.yaml") && !contains(local.kubernetes_manifest_files, "redis.yaml") && !contains(local.kubernetes_manifest_files, "rabbitmq.yaml")
    error_message = "Production app stack must not create local PostgreSQL, Redis, or RabbitMQ workloads."
  }

  assert {
    condition     = local.kubernetes_manifests["ConfigMap__auronix__auronix-config"].data.DATABASE_URL == "jdbc:postgresql://REPLACE_WITH_RDS_ENDPOINT:5432/auronix"
    error_message = "Production app stack must require an external PostgreSQL endpoint."
  }

  assert {
    condition     = local.kubernetes_manifests["ConfigMap__auronix__auronix-config"].data.RABBITMQ_URL == "amqp://REPLACE_WITH_RABBITMQ_ENDPOINT:5672/"
    error_message = "Production app stack must require an external RabbitMQ endpoint."
  }

  assert {
    condition     = local.kubernetes_manifests["ConfigMap__auronix__auronix-config"].data.REDIS_URL == "redis://REPLACE_WITH_REDIS_ENDPOINT:6379"
    error_message = "Production app stack must require an external Redis endpoint."
  }
}

run "server_workload_keeps_operational_contract" {
  command = plan

  assert {
    condition     = local.kubernetes_manifests["Deployment__auronix__server"].spec.replicas == 2
    error_message = "Production server deployment must keep at least two replicas."
  }

  assert {
    condition     = local.kubernetes_manifests["HorizontalPodAutoscaler__auronix__server"].spec.minReplicas == 2 && local.kubernetes_manifests["HorizontalPodAutoscaler__auronix__server"].spec.maxReplicas == 3
    error_message = "Production HPA must keep the expected replica bounds."
  }

  assert {
    condition     = local.kubernetes_manifests["Deployment__auronix__server"].spec.template.spec.terminationGracePeriodSeconds == 45
    error_message = "Server deployment must keep enough termination grace for Spring shutdown."
  }

  assert {
    condition     = local.kubernetes_manifests["Deployment__auronix__server"].spec.template.spec.containers[0].startupProbe.httpGet.path == "/actuator/health/liveness"
    error_message = "Server deployment must expose startupProbe on liveness health."
  }

  assert {
    condition     = local.kubernetes_manifests["Deployment__auronix__server"].spec.template.spec.containers[0].livenessProbe.httpGet.path == "/actuator/health/liveness"
    error_message = "Server deployment must expose livenessProbe."
  }

  assert {
    condition     = local.kubernetes_manifests["Deployment__auronix__server"].spec.template.spec.containers[0].readinessProbe.httpGet.path == "/actuator/health/readiness"
    error_message = "Server deployment must expose readinessProbe."
  }
}
