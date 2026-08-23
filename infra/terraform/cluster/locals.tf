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

  vpc_config = {
    name                 = local.name
    cidr                 = var.vpc_cidr
    azs                  = local.azs
    private_subnets      = local.private_subnets
    public_subnets       = local.public_subnets
    enable_dns_hostnames = true
    enable_dns_support   = true
    enable_nat_gateway   = true
    single_nat_gateway   = var.single_nat_gateway
    public_subnet_tags = {
      "kubernetes.io/role/elb"              = "1"
      "kubernetes.io/cluster/${local.name}" = "shared"
    }
    private_subnet_tags = {
      "kubernetes.io/role/internal-elb"     = "1"
      "kubernetes.io/cluster/${local.name}" = "shared"
    }
  }

  node_security_group_rules = merge(
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

  eks_config = {
    cluster_name                                 = local.name
    cluster_version                              = var.cluster_version
    cluster_service_ipv4_cidr                    = var.cluster_service_ipv4_cidr
    cluster_endpoint_public_access               = var.cluster_endpoint_public_access
    cluster_endpoint_private_access              = var.cluster_endpoint_private_access
    cluster_endpoint_public_access_cidrs         = var.cluster_endpoint_public_access_cidrs
    enable_cluster_creator_admin_permissions     = true
    node_security_group_enable_recommended_rules = false
    node_security_group_additional_rules         = local.node_security_group_rules
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
        min_size       = var.node_min_size
        max_size       = var.node_max_size
        desired_size   = var.node_desired_size
      }
    }
  }
}
