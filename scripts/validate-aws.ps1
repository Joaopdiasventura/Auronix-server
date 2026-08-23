param(
    [string]$AwsRegion = "us-east-1",
    [string]$ClusterName = "auronix-dev",
    [string]$TerraformStack = "cluster"
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [string]$Name,
        [string[]]$Arguments
    )

    & $Name @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Name $($Arguments -join ' ') failed"
    }
}

$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$identityOutput = aws sts get-caller-identity --output json 2>&1
$stsExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
if ($stsExitCode -ne 0) {
    Write-Host "AWS production validation skipped because credentials are unavailable or expired."
    Write-Host $identityOutput
    exit 2
}

$identity = $identityOutput | ConvertFrom-Json
Write-Host "AWS account: $($identity.Account)"
Write-Host "AWS arn: $($identity.Arn)"
Write-Host "AWS region: $AwsRegion"
Write-Host "EKS cluster: $ClusterName"

$terraformPath = "infra/terraform/$TerraformStack"
Invoke-Native terraform @("-chdir=$terraformPath", "init")
terraform "-chdir=$terraformPath" workspace show
if ($LASTEXITCODE -ne 0) {
    throw "terraform workspace show failed"
}
Invoke-Native terraform @("-chdir=$terraformPath", "plan", "-out=../../../target/tfplan-$TerraformStack")

Invoke-Native aws @("eks", "describe-cluster", "--region", $AwsRegion, "--name", $ClusterName, "--output", "json")
Invoke-Native aws @("eks", "update-kubeconfig", "--region", $AwsRegion, "--name", $ClusterName)
Invoke-Native kubectl @("cluster-info")
Invoke-Native kubectl @("get", "nodes", "-o", "wide")
Invoke-Native kubectl @("get", "namespaces")
Invoke-Native kubectl @("apply", "--dry-run=server", "-k", "k8s/overlays/production")

kubectl diff -k k8s/overlays/production
if ($LASTEXITCODE -gt 1) {
    throw "kubectl diff failed"
}

Write-Host "AWS production validation ok"
