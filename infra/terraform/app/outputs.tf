output "namespace" {
  description = "Kubernetes namespace where the backend server was deployed."
  value       = kubernetes_manifest.namespace[local.namespace_key].object.metadata.name
}

output "server_service_name" {
  description = "Kubernetes Service name for the backend server."
  value       = kubernetes_manifest.resource[local.server_service_key].object.metadata.name
}

output "server_service_hostname" {
  description = "LoadBalancer hostname for the backend service, when available."
  value       = try(kubernetes_manifest.resource[local.server_service_key].object.status.loadBalancer.ingress[0].hostname, null)
}

output "server_service_ip" {
  description = "LoadBalancer IP for the backend service, when available."
  value       = try(kubernetes_manifest.resource[local.server_service_key].object.status.loadBalancer.ingress[0].ip, null)
}
