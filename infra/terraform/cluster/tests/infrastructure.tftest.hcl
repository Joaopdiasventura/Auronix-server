mock_provider "aws" {}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c", "us-east-1d"]
  }
}

override_module {
  target = module.vpc
  outputs = {
    vpc_id          = "vpc-auronix-test"
    private_subnets = ["subnet-private-a", "subnet-private-b", "subnet-private-c"]
  }
}

override_module {
  target = module.eks
  outputs = {
    cluster_name           = "auronix-production"
    cluster_endpoint       = "https://eks.auronix.test"
    node_security_group_id = "sg-auronix-node"
  }
}

run "production_vpc_configuration" {
  command = plan

  variables {
    environment = "production"
    vpc_cidr    = "10.42.0.0/16"
    az_count    = 3
  }

  assert {
    condition     = local.vpc_config.name == "auronix-production"
    error_message = "VPC name must include the production environment."
  }

  assert {
    condition     = local.vpc_config.cidr == "10.42.0.0/16"
    error_message = "VPC CIDR must come from vpc_cidr."
  }

  assert {
    condition     = length(local.vpc_config.azs) == 3 && length(local.vpc_config.private_subnets) == 3 && length(local.vpc_config.public_subnets) == 3
    error_message = "VPC must create one public and one private subnet per selected AZ."
  }

  assert {
    condition     = local.vpc_config.enable_dns_support && local.vpc_config.enable_dns_hostnames
    error_message = "VPC DNS support and hostnames must be enabled for EKS."
  }

  assert {
    condition     = local.vpc_config.enable_nat_gateway && local.vpc_config.single_nat_gateway
    error_message = "VPC must keep the configured NAT strategy."
  }

  assert {
    condition     = local.vpc_config.public_subnet_tags["kubernetes.io/role/elb"] == "1" && local.vpc_config.private_subnet_tags["kubernetes.io/role/internal-elb"] == "1"
    error_message = "VPC subnets must keep Kubernetes load balancer tags."
  }
}

run "production_eks_configuration" {
  command = plan

  variables {
    environment                          = "production"
    cluster_version                      = "1.31"
    node_min_size                        = 2
    node_desired_size                    = 3
    node_max_size                        = 4
    node_https_egress_cidrs              = ["10.42.0.0/16"]
    cluster_endpoint_private_access      = true
    cluster_endpoint_public_access       = false
    cluster_endpoint_public_access_cidrs = []
  }

  assert {
    condition     = local.eks_config.cluster_name == "auronix-production"
    error_message = "EKS cluster name must include the production environment."
  }

  assert {
    condition     = local.eks_config.cluster_version == "1.31"
    error_message = "EKS cluster version must come from cluster_version."
  }

  assert {
    condition     = local.eks_config.cluster_endpoint_private_access && !local.eks_config.cluster_endpoint_public_access && length(local.eks_config.cluster_endpoint_public_access_cidrs) == 0
    error_message = "EKS API endpoint must remain private by default for production."
  }

  assert {
    condition     = local.eks_config.enable_cluster_creator_admin_permissions
    error_message = "EKS IAM creator permissions must remain enabled."
  }

  assert {
    condition     = contains(keys(local.eks_config.cluster_addons), "coredns") && contains(keys(local.eks_config.cluster_addons), "kube-proxy") && contains(keys(local.eks_config.cluster_addons), "vpc-cni")
    error_message = "EKS core addons must be configured."
  }

  assert {
    condition     = join(",", local.eks_config.node_security_group_additional_rules.egress_https.cidr_blocks) == "10.42.0.0/16"
    error_message = "EKS node HTTPS egress must use the configured CIDRs."
  }
}

run "node_group_alternative_size" {
  command = plan

  variables {
    environment         = "staging"
    node_min_size       = 1
    node_desired_size   = 2
    node_max_size       = 5
    node_instance_types = ["t3.medium", "t3.large"]
  }

  assert {
    condition     = local.eks_config.eks_managed_node_groups.default.min_size <= local.eks_config.eks_managed_node_groups.default.desired_size && local.eks_config.eks_managed_node_groups.default.desired_size <= local.eks_config.eks_managed_node_groups.default.max_size
    error_message = "Managed node group desired size must stay between min and max."
  }

  assert {
    condition     = local.eks_config.eks_managed_node_groups.default.name == "auronix-staging-default"
    error_message = "Managed node group name must include the environment."
  }

  assert {
    condition     = join(",", local.eks_config.eks_managed_node_groups.default.instance_types) == "t3.medium,t3.large"
    error_message = "Managed node group must use the configured instance types."
  }
}

run "essential_outputs" {
  command = plan

  variables {
    environment = "production"
    az_count    = 3
  }

  assert {
    condition     = output.cluster_name == "auronix-production"
    error_message = "cluster_name output must expose the EKS cluster name."
  }

  assert {
    condition     = output.cluster_endpoint == "https://eks.auronix.test"
    error_message = "cluster_endpoint output must expose the EKS API endpoint."
  }

  assert {
    condition     = output.vpc_id == "vpc-auronix-test"
    error_message = "vpc_id output must expose the VPC ID."
  }

  assert {
    condition     = output.private_subnet_ids == ["subnet-private-a", "subnet-private-b", "subnet-private-c"]
    error_message = "private_subnet_ids output must expose private subnets."
  }

  assert {
    condition     = output.node_security_group_id == "sg-auronix-node"
    error_message = "node_security_group_id output must expose the EKS node security group."
  }
}
