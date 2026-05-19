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
