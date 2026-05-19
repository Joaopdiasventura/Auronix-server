resource "kubernetes_manifest" "namespace" {
  for_each = local.namespace_manifests

  manifest = each.value
}

resource "kubernetes_manifest" "resource" {
  for_each = local.namespaced_manifests

  manifest = each.value

  depends_on = [kubernetes_manifest.namespace]
}
