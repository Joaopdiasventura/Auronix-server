resource "kubernetes_deployment_v1" "server" {
  metadata {
    name      = "server"
    namespace = var.namespace
    labels    = var.labels
  }

  spec {
    replicas = var.server_replicas

    strategy {
      type = "Recreate"
    }

    selector {
      match_labels = var.labels
    }

    template {
      metadata {
        labels = var.labels
      }

      spec {
        container {
          name              = "server"
          image             = "${var.server_image}:${var.server_image_tag}"
          image_pull_policy = "Always"

          port {
            name           = "http"
            container_port = 8080
          }

          env_from {
            config_map_ref {
              name = var.config_map_name
            }
          }

          env_from {
            secret_ref {
              name = var.secret_name
            }
          }

          resources {
            requests = {
              cpu    = var.server_cpu_request
              memory = var.server_memory_request
            }

            limits = {
              cpu    = var.server_cpu_limit
              memory = var.server_memory_limit
            }
          }

          readiness_probe {
            http_get {
              path = "/actuator/health"
              port = "http"
            }

            initial_delay_seconds = 30
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 6
          }

          liveness_probe {
            http_get {
              path = "/actuator/health"
              port = "http"
            }

            initial_delay_seconds = 60
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }
        }
      }
    }
  }
}

resource "kubernetes_service_v1" "server" {
  metadata {
    name      = "auronix-server"
    namespace = var.namespace
    labels    = var.labels
  }

  spec {
    selector                    = var.labels
    type                        = var.server_service_type
    load_balancer_source_ranges = var.server_service_type == "LoadBalancer" ? var.server_load_balancer_source_ranges : null

    port {
      name        = "http"
      port        = 80
      target_port = "http"
    }
  }
}
