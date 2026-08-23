module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = local.vpc_config.name
  cidr = local.vpc_config.cidr

  azs             = local.vpc_config.azs
  private_subnets = local.vpc_config.private_subnets
  public_subnets  = local.vpc_config.public_subnets

  enable_dns_hostnames = local.vpc_config.enable_dns_hostnames
  enable_dns_support   = local.vpc_config.enable_dns_support

  enable_nat_gateway = local.vpc_config.enable_nat_gateway
  single_nat_gateway = local.vpc_config.single_nat_gateway

  public_subnet_tags = local.vpc_config.public_subnet_tags

  private_subnet_tags = local.vpc_config.private_subnet_tags
}
