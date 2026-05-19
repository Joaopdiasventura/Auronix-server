output "namespace" {
  description = "Kubernetes namespace name."
  value       = kubernetes_namespace_v1.auronix.metadata[0].name
}
