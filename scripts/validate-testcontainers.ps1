$ErrorActionPreference = "Stop"

docker ps | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Docker is required for Testcontainers validation"
}

.\mvnw.cmd -q -Dtest=FinancePostgresIntegrationTest test

$report = "target/surefire-reports/TEST-dev.joaopdias.auronix.integration.FinancePostgresIntegrationTest.xml"
if (-not (Test-Path $report)) {
    throw "Testcontainers report was not generated"
}

[xml]$xml = Get-Content $report
$tests = [int]$xml.testsuite.tests
$failures = [int]$xml.testsuite.failures
$errors = [int]$xml.testsuite.errors
$skipped = [int]$xml.testsuite.skipped

if ($tests -lt 1) {
    throw "No Testcontainers tests were discovered"
}

if ($failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
    throw "Testcontainers validation failed or skipped tests: tests=$tests failures=$failures errors=$errors skipped=$skipped"
}

Write-Host "Testcontainers validation ok: tests=$tests failures=$failures errors=$errors skipped=$skipped"
