$ErrorActionPreference = "Stop"

$stacks = @("cluster", "app")

foreach ($stack in $stacks) {
    $path = "infra\terraform\$stack"
    Write-Host "Validating Terraform stack $stack"
    terraform "-chdir=$path" fmt -check -recursive
    if ($LASTEXITCODE -ne 0) {
        throw "terraform fmt failed for $stack"
    }
    terraform "-chdir=$path" init -backend=false
    if ($LASTEXITCODE -ne 0) {
        throw "terraform init failed for $stack"
    }
    terraform "-chdir=$path" validate
    if ($LASTEXITCODE -ne 0) {
        throw "terraform validate failed for $stack"
    }
}

$trivy = Get-Command trivy -ErrorAction SilentlyContinue
if ($trivy) {
    trivy config --severity HIGH,CRITICAL --exit-code 1 --skip-dirs "infra/terraform/cluster/.terraform" --skip-dirs "infra/terraform/app/.terraform" infra/terraform
} else {
    docker run --rm -v "$(Get-Location):/work" aquasec/trivy:latest config --severity HIGH,CRITICAL --exit-code 1 --skip-dirs /work/infra/terraform/cluster/.terraform --skip-dirs /work/infra/terraform/app/.terraform /work/infra/terraform
}

if ($LASTEXITCODE -ne 0) {
    throw "Terraform security validation failed"
}

Write-Host "Terraform validation ok"
