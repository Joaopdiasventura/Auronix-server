module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name              = local.eks_config.cluster_name
  cluster_version           = local.eks_config.cluster_version
  cluster_service_ipv4_cidr = local.eks_config.cluster_service_ipv4_cidr

  cluster_endpoint_public_access           = local.eks_config.cluster_endpoint_public_access
  cluster_endpoint_private_access          = local.eks_config.cluster_endpoint_private_access
  cluster_endpoint_public_access_cidrs     = local.eks_config.cluster_endpoint_public_access_cidrs
  enable_cluster_creator_admin_permissions = local.eks_config.enable_cluster_creator_admin_permissions

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  node_security_group_enable_recommended_rules = local.eks_config.node_security_group_enable_recommended_rules
  node_security_group_additional_rules         = local.eks_config.node_security_group_additional_rules

  cluster_addons = local.eks_config.cluster_addons

  eks_managed_node_groups = {
    default = merge(
      local.eks_config.eks_managed_node_groups.default,
      {
        subnet_ids = module.vpc.private_subnets
      },
    )
  }
}
