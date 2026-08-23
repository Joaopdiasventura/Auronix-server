$ErrorActionPreference = "Stop"

$overlays = @("local", "staging", "production")
$outputDirectory = Join-Path (Get-Location) "target\k8s"
New-Item -ItemType Directory -Force $outputDirectory | Out-Null

foreach ($overlay in $overlays) {
    $output = Join-Path $outputDirectory "$overlay.yaml"
    kubectl kustomize "k8s\overlays\$overlay" | Set-Content $output
    if ($LASTEXITCODE -ne 0) {
        throw "Kustomize render failed for $overlay"
    }
    Write-Host "Rendered $overlay to $output"
}

$kubeconform = Get-Command kubeconform -ErrorAction SilentlyContinue
if ($kubeconform) {
    kubeconform -strict -summary -ignore-missing-schemas "$outputDirectory\*.yaml"
    if ($LASTEXITCODE -ne 0) {
        throw "kubeconform validation failed"
    }
} else {
    docker run --rm -v "$(Get-Location):/work" ghcr.io/yannh/kubeconform:latest -strict -summary -ignore-missing-schemas /work/target/k8s/local.yaml /work/target/k8s/staging.yaml /work/target/k8s/production.yaml
    if ($LASTEXITCODE -ne 0) {
        throw "kubeconform validation failed"
    }
}

Write-Host "Kubernetes offline validation ok"
