param(
    [string]$ClusterName = "auronix-validation",
    [string]$NodeImage = "kindest/node:v1.36.1",
    [switch]$KeepCluster,
    [switch]$Destroy
)

$ErrorActionPreference = "Stop"

$context = "kind-$ClusterName"
$namespace = "auronix"
$imageV1 = "auronix-server:kind-v1"
$imageV2 = "auronix-server:kind-v2"
$portForwardJob = $null

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required for local Kubernetes validation"
    }
}

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') failed"
    }
}

function Invoke-Kubectl {
    $arguments = @($args)
    if ($arguments.Count -eq 1) {
        $arguments = $arguments[0] -split " "
    }
    Invoke-Native kubectl (@("--context", $context) + $arguments)
}

function Get-KubectlOutput {
    $arguments = @($args)
    if ($arguments.Count -eq 1) {
        $arguments = $arguments[0] -split " "
    }
    $output = & kubectl --context $context @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl $($arguments -join ' ') failed"
    }
    return $output
}

function Assert-KindContext {
    $current = kubectl config current-context
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl config current-context failed"
    }
    if ($current.Trim() -ne $context) {
        throw "Unexpected Kubernetes context $current"
    }
    if (-not $current.Trim().StartsWith("kind-")) {
        throw "Kubernetes context is not a kind context: $current"
    }
    Write-Host "Kubernetes context ok: $current"
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
        [int]$TimeoutSeconds = 180
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

function Wait-Rollout {
    param([string]$Resource)
    Invoke-Kubectl @("-n", $namespace, "rollout", "status", $Resource, "--timeout=420s")
}

function Assert-NoBadPods {
    $json = (Get-KubectlOutput @("-n", $namespace, "get", "pods", "-o", "json")) | ConvertFrom-Json
    foreach ($pod in $json.items) {
        if ($pod.status.phase -eq "Pending" -or $pod.status.phase -eq "Failed" -or $pod.status.phase -eq "Unknown") {
            throw "Pod $($pod.metadata.name) is $($pod.status.phase)"
        }
        foreach ($container in $pod.status.containerStatuses) {
            if ($container.state.waiting) {
                $reason = $container.state.waiting.reason
                if ($reason -in @("CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "Error")) {
                    throw "Pod $($pod.metadata.name) container $($container.name) is $reason"
                }
            }
        }
    }
}

function Start-PortForward {
    if ($script:portForwardJob) {
        Stop-Job $script:portForwardJob -ErrorAction SilentlyContinue
        Remove-Job $script:portForwardJob -Force -ErrorAction SilentlyContinue
    }
    $script:portForwardJob = Start-Job -ArgumentList $context, $namespace -ScriptBlock {
        param($JobContext, $JobNamespace)
        kubectl --context $JobContext -n $JobNamespace port-forward service/auronix-server 18080:80
    }
    Start-Sleep -Seconds 5
}

function Stop-PortForward {
    if ($script:portForwardJob) {
        Stop-Job $script:portForwardJob -ErrorAction SilentlyContinue
        Receive-Job $script:portForwardJob -ErrorAction SilentlyContinue | Out-Host
        Remove-Job $script:portForwardJob -Force -ErrorAction SilentlyContinue
        $script:portForwardJob = $null
    }
}

function Test-Health {
    Start-PortForward
    Wait-HttpOk "http://localhost:18080/actuator/health"
    Wait-HttpOk "http://localhost:18080/actuator/health/liveness"
    Wait-HttpOk "http://localhost:18080/actuator/health/readiness"
}

function Test-Dependencies {
    Invoke-Kubectl @("-n", $namespace, "exec", "statefulset/postgres", "--", "pg_isready", "-U", "auronix", "-d", "auronix")
    Invoke-Kubectl @("-n", $namespace, "exec", "deployment/rabbitmq", "--", "rabbitmq-diagnostics", "-q", "ping")
    Invoke-Kubectl @("-n", $namespace, "exec", "deployment/rabbitmq", "--", "rabbitmq-diagnostics", "-q", "check_port_connectivity")
    Invoke-Kubectl @("-n", $namespace, "exec", "deployment/redis", "--", "redis-cli", "ping")
    Write-Host "dependency connectivity ok"
}

function Test-Probes {
    $pod = Get-KubectlOutput @("-n", $namespace, "get", "pods", "-l", "app.kubernetes.io/component=server", "-o", "jsonpath={.items[0].metadata.name}")
    Invoke-Kubectl @("-n", $namespace, "describe", "pod", $pod)
    $json = (Get-KubectlOutput @("-n", $namespace, "get", "pod", $pod, "-o", "json")) | ConvertFrom-Json
    $container = $json.spec.containers | Where-Object { $_.name -eq "server" } | Select-Object -First 1
    if (-not $container.startupProbe) {
        throw "server startupProbe is missing"
    }
    if (-not $container.livenessProbe) {
        throw "server livenessProbe is missing"
    }
    if (-not $container.readinessProbe) {
        throw "server readinessProbe is missing"
    }
    Write-Host "probes ok"
}

function Test-PodRestart {
    $oldPod = Get-KubectlOutput @("-n", $namespace, "get", "pods", "-l", "app.kubernetes.io/component=server", "-o", "jsonpath={.items[0].metadata.name}")
    Invoke-Kubectl @("-n", $namespace, "logs", $oldPod, "--tail=80")
    Invoke-Kubectl @("-n", $namespace, "delete", "pod", $oldPod)
    Wait-Rollout "deployment/server"
    $newPod = Get-KubectlOutput @("-n", $namespace, "get", "pods", "-l", "app.kubernetes.io/component=server", "-o", "jsonpath={.items[0].metadata.name}")
    if ($newPod.Trim() -eq $oldPod.Trim()) {
        throw "server pod was not replaced"
    }
    Test-Health
    Write-Host "pod restart ok"
}

function Test-GracefulShutdown {
    $pod = Get-KubectlOutput @("-n", $namespace, "get", "pods", "-l", "app.kubernetes.io/component=server", "-o", "jsonpath={.items[0].metadata.name}")
    $logJob = Start-Job -ArgumentList $context, $namespace, $pod -ScriptBlock {
        param($JobContext, $JobNamespace, $JobPod)
        kubectl --context $JobContext -n $JobNamespace logs -f $JobPod
    }
    Start-Sleep -Seconds 3
    $started = Get-Date
    Invoke-Kubectl @("-n", $namespace, "delete", "pod", $pod, "--wait=false")
    Start-Sleep -Seconds 15
    Invoke-Kubectl @("-n", $namespace, "wait", "pod", $pod, "--for=delete", "--timeout=60s")
    $elapsed = [int]((Get-Date) - $started).TotalSeconds
    Wait-Rollout "deployment/server"
    Stop-Job $logJob -ErrorAction SilentlyContinue
    $logs = Receive-Job $logJob -ErrorAction SilentlyContinue
    Remove-Job $logJob -Force -ErrorAction SilentlyContinue
    $joinedLogs = $logs -join "`n"
    if ($joinedLogs -notmatch "Commencing graceful shutdown") {
        throw "Spring graceful shutdown was not observed in pod logs"
    }
    if ($elapsed -gt 45) {
        throw "Pod shutdown took $elapsed seconds, exceeding terminationGracePeriodSeconds"
    }
    Write-Host "graceful shutdown ok: ${elapsed}s"
}

function Test-RollingUpdateAndRollback {
    Invoke-Kubectl @("-n", $namespace, "set", "image", "deployment/server", "server=$imageV2")
    Wait-Rollout "deployment/server"
    Test-Health
    $image = Get-KubectlOutput @("-n", $namespace, "get", "deployment", "server", "-o", "jsonpath={.spec.template.spec.containers[0].image}")
    if ($image.Trim() -ne $imageV2) {
        throw "Deployment image is $image instead of $imageV2"
    }
    Invoke-Kubectl @("-n", $namespace, "rollout", "history", "deployment/server")
    Invoke-Kubectl @("-n", $namespace, "rollout", "undo", "deployment/server")
    Wait-Rollout "deployment/server"
    Test-Health
    $rollbackImage = Get-KubectlOutput @("-n", $namespace, "get", "deployment", "server", "-o", "jsonpath={.spec.template.spec.containers[0].image}")
    if ($rollbackImage.Trim() -ne $imageV1) {
        throw "Rollback image is $rollbackImage instead of $imageV1"
    }
    Write-Host "rolling update and rollback ok"
}

function Test-ReadinessFailure {
    try {
        Invoke-Kubectl @("-n", $namespace, "scale", "statefulset/postgres", "--replicas=0")
        $restartCount = $null
        $readyBecameFalse = $false
        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            $podJson = (Get-KubectlOutput @("-n", $namespace, "get", "pods", "-l", "app.kubernetes.io/component=server", "-o", "json")) | ConvertFrom-Json
            $serverPod = $podJson.items[0]
            $readyCondition = $serverPod.status.conditions | Where-Object { $_.type -eq "Ready" } | Select-Object -First 1
            $currentRestartCount = ($serverPod.status.containerStatuses | Where-Object { $_.name -eq "server" } | Select-Object -First 1).restartCount
            if ($null -eq $restartCount) {
                $restartCount = $currentRestartCount
            }
            if ($currentRestartCount -ne $restartCount) {
                throw "Liveness restarted the server while PostgreSQL readiness was unavailable"
            }
            if ($readyCondition.status -eq "False") {
                $readyBecameFalse = $true
                break
            }
            Start-Sleep -Seconds 5
        }
        if (-not $readyBecameFalse) {
            throw "Readiness did not become false while PostgreSQL was unavailable"
        }
    } finally {
        Invoke-Kubectl @("-n", $namespace, "scale", "statefulset/postgres", "--replicas=1")
        Wait-Rollout "statefulset/postgres"
        Wait-Rollout "deployment/server"
        Test-Health
    }
    Write-Host "readiness failure ok"
}

function Test-MultipleReplicas {
    Invoke-Kubectl @("-n", $namespace, "scale", "deployment/server", "--replicas=3")
    Wait-Rollout "deployment/server"
    Test-Health
    $ready = Get-KubectlOutput @("-n", $namespace, "get", "deployment", "server", "-o", "jsonpath={.status.readyReplicas}")
    if ($ready.Trim() -ne "3") {
        throw "Expected 3 ready server replicas, got $ready"
    }
    Invoke-Kubectl @("-n", $namespace, "logs", "deployment/server", "--tail=120")
    Write-Host "multiple replicas ok"
}

function Collect-Diagnostics {
    try {
        $contexts = kubectl config get-contexts -o name
        if ($contexts -notcontains $context) {
            Write-Host "No diagnostics collected because $context does not exist"
            return
        }
        Invoke-Kubectl @("-n", $namespace, "get", "pods", "-o", "wide")
        Invoke-Kubectl @("-n", $namespace, "get", "deployments")
        Invoke-Kubectl @("-n", $namespace, "get", "statefulsets")
        Invoke-Kubectl @("-n", $namespace, "get", "services")
        Invoke-Kubectl @("-n", $namespace, "get", "events", "--sort-by=.lastTimestamp")
        Invoke-Kubectl @("-n", $namespace, "logs", "deployment/server", "--tail=160")
    } catch {
        Write-Host $_
    }
}

function Ensure-KindCluster {
    $clusters = kind get clusters
    if ($clusters -contains $ClusterName) {
        return
    }
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        try {
            Invoke-Native kind @("create", "cluster", "--name", $ClusterName, "--image", $NodeImage, "--wait", "5m")
            return
        } catch {
            if ($attempt -eq 2) {
                throw
            }
            kind delete cluster --name $ClusterName
            Start-Sleep -Seconds 10
        }
    }
}

try {
    Require-Command docker
    Require-Command kubectl
    Require-Command kind

    kind version
    docker version
    kubectl version --client=true

    Invoke-Native docker @("build", "-t", $imageV1, "-t", $imageV2, ".")

    Ensure-KindCluster

    Invoke-Native kind @("load", "docker-image", $imageV1, "--name", $ClusterName)
    Invoke-Native kind @("load", "docker-image", $imageV2, "--name", $ClusterName)

    Invoke-Native kubectl @("config", "use-context", $context)
    Assert-KindContext
    Invoke-Kubectl @("cluster-info")

    & (Join-Path $PSScriptRoot "validate-k8s-offline.ps1")
    if ($LASTEXITCODE -ne 0) {
        throw "Kubernetes offline validation failed"
    }

    Invoke-Kubectl @("apply", "-k", "k8s/overlays/local")
    Wait-Rollout "statefulset/postgres"
    Wait-Rollout "deployment/rabbitmq"
    Wait-Rollout "deployment/redis"
    Wait-Rollout "deployment/server"
    Assert-NoBadPods
    Collect-Diagnostics
    Test-Probes
    Test-Dependencies
    Test-Health
    Test-PodRestart
    Test-GracefulShutdown
    Test-RollingUpdateAndRollback
    Test-ReadinessFailure
    Test-MultipleReplicas
    Assert-NoBadPods
    Collect-Diagnostics

    Write-Host "kind validation ok"
} catch {
    Write-Host "kind validation failed: $_"
    Collect-Diagnostics
    throw
} finally {
    Stop-PortForward
    if ($Destroy -or -not $KeepCluster) {
        kind delete cluster --name $ClusterName
    }
}
