resource "kubernetes_namespace_v1" "auronix" {
  metadata {
    name   = var.namespace
    labels = var.labels
  }
}
