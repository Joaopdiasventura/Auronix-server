param(
    [switch]$IncludeKind,
    [switch]$SkipCompose
)

$ErrorActionPreference = "Stop"

$maven = if ($IsWindows -or $null -eq $IsWindows) { ".\mvnw.cmd" } else { "./mvnw" }

git diff --check
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed"
}

& $maven -q test
if ($LASTEXITCODE -ne 0) {
    throw "Maven tests failed"
}

& $maven -q package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Maven package failed"
}

docker compose config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose config failed"
}

if (-not $SkipCompose) {
    & (Join-Path $PSScriptRoot "validate-local.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Local validation failed"
    }
}

& (Join-Path $PSScriptRoot "validate-testcontainers.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Testcontainers validation failed"
}

docker build -t auronix-server:local .
if ($LASTEXITCODE -ne 0) {
    throw "Docker build failed"
}

& (Join-Path $PSScriptRoot "validate-k8s-offline.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Kubernetes offline validation failed"
}

& (Join-Path $PSScriptRoot "validate-terraform.ps1")
if ($LASTEXITCODE -ne 0) {
    throw "Terraform validation failed"
}

if ($IncludeKind) {
    & (Join-Path $PSScriptRoot "validate-k8s-kind.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Kubernetes kind validation failed"
    }
}

Write-Host "All local validations passed"
