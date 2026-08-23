variable "aws_region" {
  description = "AWS region where the EKS infrastructure will be created."
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "EKS cluster base name."
  type        = string
  default     = "auronix"
}

variable "environment" {
  description = "Environment name used in resource names and tags."
  type        = string
  default     = "dev"
}

variable "cluster_version" {
  description = "EKS Kubernetes version."
  type        = string
  default     = "1.31"
}

variable "cluster_service_ipv4_cidr" {
  description = "IPv4 CIDR used by Kubernetes Services inside the EKS cluster."
  type        = string
  default     = "172.20.0.0/16"
}

variable "cluster_endpoint_public_access" {
  description = "Whether the EKS API endpoint is reachable from public networks."
  type        = bool
  default     = false
}

variable "cluster_endpoint_private_access" {
  description = "Whether the EKS API endpoint is reachable from inside the VPC."
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR ranges allowed to reach the public EKS API endpoint when public access is enabled."
  type        = list(string)
  default     = []
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to use."
  type        = number
  default     = 2
}

variable "single_nat_gateway" {
  description = "Whether to use a single NAT Gateway for private subnets."
  type        = bool
  default     = true
}

variable "node_instance_types" {
  description = "EC2 instance types for the managed node group."
  type        = list(string)
  default     = ["t3.small"]
}

variable "node_https_egress_cidrs" {
  description = "CIDR ranges that EKS nodes can reach over HTTPS."
  type        = list(string)
  default     = []
}

variable "node_security_group_additional_rules" {
  description = "Additional EKS node security group rules merged with the project defaults."
  type        = any
  default     = {}
}

variable "node_min_size" {
  description = "Minimum node count."
  type        = number
  default     = 1
}

variable "node_desired_size" {
  description = "Desired node count."
  type        = number
  default     = 3
}

variable "node_max_size" {
  description = "Maximum node count."
  type        = number
  default     = 3
}
