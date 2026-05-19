locals {
  name = "${var.app_name}-${var.environment}"

  labels = {
    "app.kubernetes.io/name"       = var.app_name
    "app.kubernetes.io/managed-by" = "terraform"
    "app.kubernetes.io/part-of"    = var.app_name
    "app.kubernetes.io/instance"   = local.name
  }

  server_labels = merge(local.labels, { "app.kubernetes.io/component" = "server" })

  server_env = merge(
    {
      PORT           = "8080"
      JPA_DDL_AUTO   = "update"
      JPA_SHOW_SQL   = "false"
      JPA_FORMAT_SQL = "false"
      CLIENT_URLS    = "http://localhost:4200"
    },
    var.server_env,
  )
}
