$ErrorActionPreference = "Stop"

function Invoke-HttpOk {
    param(
        [string]$Uri,
        [string]$Name
    )

    $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Name returned HTTP $($response.StatusCode)"
    }
    Write-Host "$Name ok"
}

function Invoke-ContainerCheck {
    param(
        [string]$Service,
        [string[]]$Command,
        [string]$Name
    )

    docker compose exec -T $Service @Command | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed"
    }
    Write-Host "$Name ok"
}

Invoke-HttpOk -Uri "http://localhost:8080/actuator/health" -Name "application health"
Invoke-HttpOk -Uri "http://localhost:8080/actuator/health/liveness" -Name "application liveness"
Invoke-HttpOk -Uri "http://localhost:8080/actuator/health/readiness" -Name "application readiness"
Invoke-ContainerCheck -Service "db" -Command @("pg_isready", "-U", "postgres", "-d", "auronix") -Name "postgres readiness"
Invoke-ContainerCheck -Service "message-br" -Command @("rabbitmq-diagnostics", "-q", "ping") -Name "rabbitmq ping"
Invoke-ContainerCheck -Service "message-br" -Command @("rabbitmq-diagnostics", "-q", "check_port_connectivity") -Name "rabbitmq port connectivity"
Invoke-ContainerCheck -Service "cache" -Command @("redis-cli", "ping") -Name "redis ping"
