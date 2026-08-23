module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name              = local.name
  cluster_version           = var.cluster_version
  cluster_service_ipv4_cidr = var.cluster_service_ipv4_cidr

  cluster_endpoint_public_access           = var.cluster_endpoint_public_access
  cluster_endpoint_private_access          = var.cluster_endpoint_private_access
  cluster_endpoint_public_access_cidrs     = var.cluster_endpoint_public_access_cidrs
  enable_cluster_creator_admin_permissions = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  node_security_group_enable_recommended_rules = false
  node_security_group_additional_rules = merge(
    {
      ingress_nodes_ephemeral = {
        description = "Node to node ingress on ephemeral ports"
        protocol    = "tcp"
        from_port   = 1025
        to_port     = 65535
        type        = "ingress"
        self        = true
      }
      ingress_cluster_4443_webhook = {
        description                   = "Cluster API to node 4443/tcp webhook"
        protocol                      = "tcp"
        from_port                     = 4443
        to_port                       = 4443
        type                          = "ingress"
        source_cluster_security_group = true
      }
      ingress_cluster_6443_webhook = {
        description                   = "Cluster API to node 6443/tcp webhook"
        protocol                      = "tcp"
        from_port                     = 6443
        to_port                       = 6443
        type                          = "ingress"
        source_cluster_security_group = true
      }
      ingress_cluster_8443_webhook = {
        description                   = "Cluster API to node 8443/tcp webhook"
        protocol                      = "tcp"
        from_port                     = 8443
        to_port                       = 8443
        type                          = "ingress"
        source_cluster_security_group = true
      }
      ingress_cluster_9443_webhook = {
        description                   = "Cluster API to node 9443/tcp webhook"
        protocol                      = "tcp"
        from_port                     = 9443
        to_port                       = 9443
        type                          = "ingress"
        source_cluster_security_group = true
      }
      egress_https = {
        description = "Node HTTPS egress"
        protocol    = "tcp"
        from_port   = 443
        to_port     = 443
        type        = "egress"
        cidr_blocks = coalescelist(var.node_https_egress_cidrs, [var.vpc_cidr])
      }
    },
    var.node_security_group_additional_rules,
  )

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
  }

  eks_managed_node_groups = {
    default = {
      name           = "${local.name}-default"
      instance_types = var.node_instance_types

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size

      subnet_ids = module.vpc.private_subnets
    }
  }
}
