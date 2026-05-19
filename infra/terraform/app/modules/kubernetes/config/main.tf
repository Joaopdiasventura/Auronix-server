resource "kubernetes_config_map_v1" "auronix" {
  metadata {
    name      = "auronix-config"
    namespace = var.namespace
    labels    = var.labels
  }

  data = var.server_env
}

resource "kubernetes_secret_v1" "auronix" {
  metadata {
    name      = "auronix-secrets"
    namespace = var.namespace
    labels    = var.labels
  }

  type = "Opaque"

  data = var.server_secret_env
}
