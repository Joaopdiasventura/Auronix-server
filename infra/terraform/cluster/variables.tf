variable "aws_region" {
  description = "AWS region where the EKS infrastructure will be created."
  type        = string
  default     = "us-east-1"

  validation {
    condition     = trimspace(var.aws_region) != ""
    error_message = "aws_region must not be empty."
  }
}

variable "cluster_name" {
  description = "EKS cluster base name."
  type        = string
  default     = "auronix"

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9-]*$", var.cluster_name))
    error_message = "cluster_name must contain lowercase letters, numbers, and hyphens, and must start with a letter or number."
  }
}

variable "environment" {
  description = "Environment name used in resource names and tags."
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "production"], var.environment)
    error_message = "environment must be one of dev, staging, or production."
  }
}

variable "cluster_version" {
  description = "EKS Kubernetes version."
  type        = string
  default     = "1.31"

  validation {
    condition     = can(regex("^1\\.[0-9]+$", var.cluster_version))
    error_message = "cluster_version must use the major.minor Kubernetes version format."
  }
}

variable "cluster_service_ipv4_cidr" {
  description = "IPv4 CIDR used by Kubernetes Services inside the EKS cluster."
  type        = string
  default     = "172.20.0.0/16"

  validation {
    condition     = can(cidrhost(var.cluster_service_ipv4_cidr, 0))
    error_message = "cluster_service_ipv4_cidr must be a valid IPv4 CIDR block."
  }
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

  validation {
    condition     = alltrue([for cidr in var.cluster_endpoint_public_access_cidrs : can(cidrhost(cidr, 0))])
    error_message = "cluster_endpoint_public_access_cidrs must contain only valid CIDR blocks."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.40.0.0/16"

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "vpc_cidr must be a valid CIDR block."
  }
}

variable "az_count" {
  description = "Number of availability zones to use."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 6
    error_message = "az_count must be between 2 and 6."
  }
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

  validation {
    condition     = length(var.node_instance_types) > 0 && alltrue([for instance_type in var.node_instance_types : trimspace(instance_type) != ""])
    error_message = "node_instance_types must contain at least one non-empty instance type."
  }
}

variable "node_https_egress_cidrs" {
  description = "CIDR ranges that EKS nodes can reach over HTTPS."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for cidr in var.node_https_egress_cidrs : can(cidrhost(cidr, 0))])
    error_message = "node_https_egress_cidrs must contain only valid CIDR blocks."
  }
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

  validation {
    condition     = var.node_min_size >= 1
    error_message = "node_min_size must be at least 1."
  }
}

variable "node_desired_size" {
  description = "Desired node count."
  type        = number
  default     = 3

  validation {
    condition     = var.node_desired_size >= var.node_min_size && var.node_desired_size <= var.node_max_size
    error_message = "node_desired_size must be between node_min_size and node_max_size."
  }
}

variable "node_max_size" {
  description = "Maximum node count."
  type        = number
  default     = 3

  validation {
    condition     = var.node_max_size >= var.node_min_size
    error_message = "node_max_size must be greater than or equal to node_min_size."
  }
}
