variable "aws_region" {
  description = "Accepted for compatibility with shared variable files. Not used by the app stack."
  type        = string
  default     = "us-east-1"
}

variable "kubernetes_config_path" {
  description = "Path to the kubeconfig file used by the Kubernetes provider. When null, the provider uses its default discovery."
  type        = string
  default     = null
}

variable "kubernetes_config_context" {
  description = "Kubeconfig context used by the Kubernetes provider. When null, the current context is used."
  type        = string
  default     = null
}

variable "app_name" {
  description = "Application name used in Kubernetes labels."
  type        = string
  default     = "auronix"
}

variable "environment" {
  description = "Environment name used in labels."
  type        = string
  default     = "dev"
}

variable "namespace" {
  description = "Kubernetes namespace for the backend server."
  type        = string
  default     = "auronix"
}

variable "server_image" {
  description = "Docker image repository for the backend server."
  type        = string
  default     = "jpplay/auditex-server"
}

variable "server_image_tag" {
  description = "Docker image tag for the backend server."
  type        = string
  default     = "latest"
}

variable "server_replicas" {
  description = "Backend server replica count."
  type        = number
  default     = 1
}

variable "server_service_type" {
  description = "Kubernetes Service type for the backend server."
  type        = string
  default     = "LoadBalancer"

  validation {
    condition     = contains(["ClusterIP", "LoadBalancer", "NodePort"], var.server_service_type)
    error_message = "server_service_type must be ClusterIP, LoadBalancer or NodePort."
  }
}

variable "server_load_balancer_source_ranges" {
  description = "CIDR ranges allowed to access the backend LoadBalancer."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "server_cpu_request" {
  description = "Backend server CPU request."
  type        = string
  default     = "125m"
}

variable "server_memory_request" {
  description = "Backend server memory request."
  type        = string
  default     = "256Mi"
}

variable "server_cpu_limit" {
  description = "Backend server CPU limit."
  type        = string
  default     = "500m"
}

variable "server_memory_limit" {
  description = "Backend server memory limit."
  type        = string
  default     = "512Mi"
}

variable "server_env" {
  description = "Non-sensitive environment variables injected into the backend server through a ConfigMap."
  type        = map(string)

  default = {
    DATABASE_URL   = "jdbc:postgresql://replace-with-db-host:5432/auronix"
    REDIS_URL      = "redis://replace-with-redis-host:6379"
    CLIENT_URLS    = "http://localhost:4200"
    JPA_DDL_AUTO   = "update"
    JPA_SHOW_SQL   = "false"
    JPA_FORMAT_SQL = "false"
  }
}

variable "server_secret_env" {
  description = "Sensitive environment variables injected into the backend server through a Secret."
  type        = map(string)
  sensitive   = true

  default = {
    DATABASE_USERNAME = "replace-with-database-username"
    DATABASE_PASSWORD = "replace-with-database-password"
    RABBITMQ_URL      = "amqp://replace-with-rabbitmq-host:5672/"
    JWT_SECRET        = "replace-with-a-long-random-secret"
  }
}
