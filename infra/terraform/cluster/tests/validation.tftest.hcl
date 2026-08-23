mock_provider "aws" {}

override_data {
  target = data.aws_availability_zones.available
  values = {
    names = ["us-east-1a", "us-east-1b", "us-east-1c"]
  }
}

override_module {
  target = module.vpc
  outputs = {
    vpc_id          = "vpc-auronix-test"
    private_subnets = ["subnet-private-a", "subnet-private-b"]
  }
}

override_module {
  target = module.eks
  outputs = {
    cluster_name           = "auronix-dev"
    cluster_endpoint       = "https://eks.auronix.test"
    node_security_group_id = "sg-auronix-node"
  }
}

run "invalid_environment_fails" {
  command = plan

  variables {
    environment = "qa"
  }

  expect_failures = [
    var.environment,
  ]
}

run "invalid_vpc_cidr_fails" {
  command = plan

  variables {
    vpc_cidr = "not-a-cidr"
  }

  expect_failures = [
    var.vpc_cidr,
  ]
}

run "invalid_desired_node_count_fails" {
  command = plan

  variables {
    node_min_size     = 1
    node_desired_size = 5
    node_max_size     = 3
  }

  expect_failures = [
    var.node_desired_size,
  ]
}

run "invalid_max_node_count_fails" {
  command = plan

  variables {
    node_min_size     = 4
    node_desired_size = 4
    node_max_size     = 3
  }

  expect_failures = [
    var.node_max_size,
  ]
}
