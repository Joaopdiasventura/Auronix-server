param(
    [string]$ClusterName = "auronix-local",
    [switch]$Destroy
)

$ErrorActionPreference = "Stop"
$portForwardJob = $null

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required for local Kubernetes validation"
    }
}

function Invoke-HttpOk {
    param([string]$Uri)
    $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Uri returned HTTP $($response.StatusCode)"
    }
}

function Wait-HttpOk {
    param(
        [string]$Uri,
        [int]$TimeoutSeconds = 120
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-HttpOk -Uri $Uri
            Write-Host "$Uri ok"
            return
        } catch {
            Start-Sleep -Seconds 3
        }
    }
    throw "$Uri did not become healthy"
}

try {
    Require-Command docker
    Require-Command kubectl
    Require-Command kind

    docker build -t server-server:latest .

    $clusters = kind get clusters
    if ($clusters -notcontains $ClusterName) {
        kind create cluster --name $ClusterName
    }

    kind load docker-image server-server:latest --name $ClusterName

    kubectl config use-context "kind-$ClusterName"
    kubectl apply -k k8s/overlays/local

    kubectl -n auronix rollout status statefulset/postgres --timeout=300s
    kubectl -n auronix rollout status deployment/rabbitmq --timeout=300s
    kubectl -n auronix rollout status deployment/redis --timeout=300s
    kubectl -n auronix rollout status deployment/server --timeout=300s

    kubectl -n auronix get pods -o wide
    kubectl -n auronix get services

    $portForwardJob = Start-Job -ScriptBlock {
        kubectl -n auronix port-forward service/auronix-server 18080:80
    }
    Start-Sleep -Seconds 5

    Wait-HttpOk -Uri "http://localhost:18080/actuator/health"
    Wait-HttpOk -Uri "http://localhost:18080/actuator/health/liveness"
    Wait-HttpOk -Uri "http://localhost:18080/actuator/health/readiness"

    kubectl -n auronix rollout restart deployment/server
    kubectl -n auronix rollout status deployment/server --timeout=300s
    kubectl -n auronix rollout history deployment/server
    kubectl -n auronix rollout undo deployment/server
    kubectl -n auronix rollout status deployment/server --timeout=300s

    $pod = kubectl -n auronix get pods -l app.kubernetes.io/component=server -o jsonpath="{.items[0].metadata.name}"
    kubectl -n auronix delete pod $pod --wait=false
    kubectl -n auronix rollout status deployment/server --timeout=300s
    kubectl -n auronix get pods
    kubectl -n auronix logs deployment/server --tail=120

    Write-Host "kind validation ok"
} finally {
    if ($portForwardJob) {
        Stop-Job $portForwardJob -ErrorAction SilentlyContinue
        Remove-Job $portForwardJob -Force -ErrorAction SilentlyContinue
    }
    if ($Destroy) {
        kind delete cluster --name $ClusterName
    }
}
