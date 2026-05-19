output "server_service_name" {
  description = "Kubernetes Service name for the backend server."
  value       = kubernetes_service_v1.server.metadata[0].name
}

output "server_service_hostname" {
  description = "LoadBalancer hostname for the backend service, when available."
  value       = try(kubernetes_service_v1.server.status[0].load_balancer[0].ingress[0].hostname, null)
}

output "server_service_ip" {
  description = "LoadBalancer IP for the backend service, when available."
  value       = try(kubernetes_service_v1.server.status[0].load_balancer[0].ingress[0].ip, null)
}
