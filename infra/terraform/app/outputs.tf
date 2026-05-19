output "namespace" {
  description = "Kubernetes namespace where the backend server was deployed."
  value       = module.kubernetes_namespace.namespace
}

output "server_service_name" {
  description = "Kubernetes Service name for the backend server."
  value       = module.kubernetes_server.server_service_name
}

output "server_service_hostname" {
  description = "LoadBalancer hostname for the backend service, when available."
  value       = module.kubernetes_server.server_service_hostname
}

output "server_service_ip" {
  description = "LoadBalancer IP for the backend service, when available."
  value       = module.kubernetes_server.server_service_ip
}
