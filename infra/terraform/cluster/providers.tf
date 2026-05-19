provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.aws_tags
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}
