module "kubernetes_namespace" {
  source = "./modules/kubernetes/namespace"

  namespace = var.namespace
  labels    = local.labels
}

module "kubernetes_config" {
  source = "./modules/kubernetes/config"

  namespace = module.kubernetes_namespace.namespace
  labels    = local.labels

  server_env        = local.server_env
  server_secret_env = var.server_secret_env
}

module "kubernetes_server" {
  source = "./modules/kubernetes/server"

  namespace = module.kubernetes_namespace.namespace
  labels    = local.server_labels

  config_map_name = module.kubernetes_config.config_map_name
  secret_name     = module.kubernetes_config.secret_name

  server_image                       = var.server_image
  server_image_tag                   = var.server_image_tag
  server_replicas                    = var.server_replicas
  server_service_type                = var.server_service_type
  server_load_balancer_source_ranges = var.server_load_balancer_source_ranges
  server_cpu_request                 = var.server_cpu_request
  server_memory_request              = var.server_memory_request
  server_cpu_limit                   = var.server_cpu_limit
  server_memory_limit                = var.server_memory_limit
}
