locals {
  kubernetes_manifest_files = [
    "namespace.yaml",
    "secrets.yaml",
    "configmap.yaml",
    "metrics-server.yaml",
    "server.yaml",
    "hpa.yaml",
  ]

  kubernetes_manifest_documents = flatten([
    for file_name in local.kubernetes_manifest_files : [
      for document_index, manifest_body in split("\n---\n", replace(file("${path.module}/../../../k8s/${file_name}"), "\r\n", "\n")) : {
        file     = file_name
        index    = document_index
        manifest = yamldecode(manifest_body)
      }
      if trimspace(manifest_body) != ""
    ]
  ])

  kubernetes_manifests = {
    for document in local.kubernetes_manifest_documents :
    format(
      "%s__%s__%s",
      document.manifest.kind,
      try(document.manifest.metadata.namespace, "_cluster"),
      document.manifest.metadata.name,
      ) => jsondecode(document.manifest.kind == "Secret" && can(document.manifest.stringData) ? jsonencode(merge(
        {
          for key, value in document.manifest :
          key => value
          if key != "stringData"
        },
        {
          data = merge(
            try(document.manifest.data, {}),
            {
              for key, value in document.manifest.stringData :
              key => base64encode(value)
            },
          )
        },
    )) : jsonencode(document.manifest))
  }

  namespace_manifests = {
    for key, manifest in local.kubernetes_manifests :
    key => manifest
    if manifest.kind == "Namespace"
  }

  namespaced_manifests = {
    for key, manifest in local.kubernetes_manifests :
    key => manifest
    if manifest.kind != "Namespace"
  }

  namespace_key      = "Namespace___cluster__auronix"
  server_service_key = "Service__auronix__auronix-server"
}
