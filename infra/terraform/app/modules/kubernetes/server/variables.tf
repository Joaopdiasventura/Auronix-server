variable "namespace" {
  description = "Kubernetes namespace for the backend server."
  type        = string
}

variable "labels" {
  description = "Labels applied to backend server resources."
  type        = map(string)
}

variable "config_map_name" {
  description = "Application ConfigMap name."
  type        = string
}

variable "secret_name" {
  description = "Application Secret name."
  type        = string
}

variable "server_image" {
  description = "Docker image repository for the backend server."
  type        = string
}

variable "server_image_tag" {
  description = "Docker image tag for the backend server."
  type        = string
}

variable "server_replicas" {
  description = "Initial backend server replica count."
  type        = number
}

variable "server_service_type" {
  description = "Kubernetes Service type for the backend server."
  type        = string
}

variable "server_load_balancer_source_ranges" {
  description = "CIDR ranges allowed to access the backend LoadBalancer."
  type        = list(string)
}

variable "server_cpu_request" {
  description = "Backend server CPU request."
  type        = string
}

variable "server_memory_request" {
  description = "Backend server memory request."
  type        = string
}

variable "server_cpu_limit" {
  description = "Backend server CPU limit."
  type        = string
}

variable "server_memory_limit" {
  description = "Backend server memory limit."
  type        = string
}
