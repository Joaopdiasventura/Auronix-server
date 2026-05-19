locals {
  name = "${var.cluster_name}-${var.environment}"

  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  private_subnets = [
    for index in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, index)
  ]

  public_subnets = [
    for index in range(var.az_count) : cidrsubnet(var.vpc_cidr, 4, index + var.az_count)
  ]

  aws_tags = {
    Project     = var.cluster_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}
