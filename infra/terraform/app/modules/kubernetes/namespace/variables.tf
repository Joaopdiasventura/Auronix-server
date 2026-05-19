variable "namespace" {
  description = "Kubernetes namespace for the application."
  type        = string
}

variable "labels" {
  description = "Labels applied to the namespace."
  type        = map(string)
}
