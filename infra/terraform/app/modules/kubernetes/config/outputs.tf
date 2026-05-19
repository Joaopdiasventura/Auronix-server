output "config_map_name" {
  description = "Application ConfigMap name."
  value       = kubernetes_config_map_v1.auronix.metadata[0].name
}

output "secret_name" {
  description = "Application Secret name."
  value       = kubernetes_secret_v1.auronix.metadata[0].name
}
