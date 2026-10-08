param(
  [string]$EnvFile = ".env",
  [string]$MavenArgs = "-DskipTests spring-boot:run",
  [switch]$RequireRabbit
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

if (-not (Test-Path $EnvFile)) {
  throw "Env file not found: $EnvFile"
}

Get-Content $EnvFile | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), "Process")
  }
}

if (-not $RequireRabbit) {
  [Environment]::SetEnvironmentVariable("RABBIT_LISTENER_AUTO_STARTUP", "false", "Process")
  [Environment]::SetEnvironmentVariable("MANAGEMENT_HEALTH_RABBIT_ENABLED", "false", "Process")
  [Environment]::SetEnvironmentVariable("MANAGEMENT_ENDPOINT_HEALTH_VALIDATE_GROUP_MEMBERSHIP", "false", "Process")
}

Write-Host "[run-backend-dev] loaded $EnvFile"
Write-Host "[run-backend-dev] profile=$env:SPRING_PROFILES_ACTIVE port=$env:SERVER_PORT"
if (-not $RequireRabbit) {
  Write-Host "[run-backend-dev] RabbitMQ listener/health disabled for local dev. Use -RequireRabbit to require it."
}

$argsList = $MavenArgs -split '\s+'
& .\mvnw.cmd @argsList
