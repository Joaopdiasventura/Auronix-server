$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Name"
    & $Action
}

function Wait-ServiceHealthy {
    param(
        [string]$Service,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $status = docker compose ps --format json $Service | ConvertFrom-Json
        if ($status.Health -eq "healthy" -or ($Service -eq "server" -and $status.State -eq "running" -and $status.Health -eq "")) {
            Write-Host "$Service healthy"
            return
        }
        Start-Sleep -Seconds 5
    }
    docker compose ps
    throw "$Service did not become healthy within $TimeoutSeconds seconds"
}

Invoke-Step "Docker" {
    docker version
}

Invoke-Step "Docker Compose" {
    docker compose version
    docker compose config --quiet
}

Invoke-Step "Start containers" {
    docker compose up -d --build
    docker compose ps
}

Invoke-Step "Wait for service health" {
    Wait-ServiceHealthy -Service "db"
    Wait-ServiceHealthy -Service "message-br"
    Wait-ServiceHealthy -Service "cache"
    Wait-ServiceHealthy -Service "server" -TimeoutSeconds 240
}

Invoke-Step "Smoke tests" {
    & "$PSScriptRoot\smoke-local.ps1"
}

Invoke-Step "Maven tests" {
    .\mvnw.cmd -q test
}

Invoke-Step "Maven package" {
    .\mvnw.cmd -q package -DskipTests
}
