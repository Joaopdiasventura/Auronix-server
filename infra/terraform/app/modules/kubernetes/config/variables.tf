variable "namespace" {
  description = "Kubernetes namespace for the application."
  type        = string
}

variable "labels" {
  description = "Labels applied to configuration resources."
  type        = map(string)
}

variable "server_env" {
  description = "Non-sensitive environment variables injected into the backend server."
  type        = map(string)
}

variable "server_secret_env" {
  description = "Sensitive environment variables injected into the backend server."
  type        = map(string)
  sensitive   = true
}
