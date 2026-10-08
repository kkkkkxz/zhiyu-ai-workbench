param(
  [string]$EnvFile = ".env",
  [string]$ComposeFile = "docker-compose.deploy.yml",
  [string]$NginxConf = "nginx/app.conf"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$failed = $false

function Err($msg) {
  Write-Host "ERROR: $msg" -ForegroundColor Red
  $script:failed = $true
}

function Warn($msg) {
  Write-Host "WARN: $msg" -ForegroundColor Yellow
}

function Ok($msg) {
  Write-Host "OK: $msg" -ForegroundColor Green
}

if (-not (Test-Path $EnvFile)) {
  Err "$EnvFile not found. Run .\deploy.ps1 once to generate it."
  $envMap = @{}
} else {
  $envMap = @{}
  Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') { $envMap[$matches[1].Trim()] = $matches[2].Trim() }
  }
  Ok "loaded $EnvFile"
}

$required = @(
  "DASHSCOPE_API_KEY", "JWT_SECRET", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD",
  "REDIS_PASSWORD", "RABBIT_PASSWORD", "QDRANT_API_KEY", "MINIO_ACCESS_KEY",
  "MINIO_SECRET_KEY"
)
$weak = @("123456", "root", "root123", "password", "admin", "guest", "zwsk_dev_pwd", "zwsk_root_pwd", "zwsk_redis_pwd", "minioadmin", "minio_dev_pwd_change_me")

foreach ($key in $required) {
  $value = $envMap[$key]
  if ([string]::IsNullOrWhiteSpace($value)) { Err "$key is empty" }
  if ($weak -contains $value) { Err "$key uses weak/default value" }
}

$jwtSecret = if ($envMap.ContainsKey("JWT_SECRET")) { $envMap["JWT_SECRET"] } else { "" }
if ($jwtSecret.Length -lt 64) {
  Err "JWT_SECRET must be at least 64 characters"
}

$dashscopeKey = if ($envMap.ContainsKey("DASHSCOPE_API_KEY")) { $envMap["DASHSCOPE_API_KEY"] } else { "" }
if ($dashscopeKey -notlike "sk-*") {
  Warn "DASHSCOPE_API_KEY does not look like a DashScope key"
}

if (-not (Test-Path $ComposeFile)) {
  Err "$ComposeFile not found"
} else {
  $compose = Get-Content -Raw $ComposeFile
  if ($compose -match "3306:3306|6379:6379|5672:5672|6333:6333|9000:9000|9001:9001|15672:15672") {
    Err "$ComposeFile appears to publish internal middleware ports"
  } else {
    Ok "middleware ports are not published by compose"
  }
}

if (-not (Test-Path $NginxConf)) {
  Err "$NginxConf not found"
} else {
  $nginx = Get-Content -Raw $NginxConf
  $publicDomain = if ($envMap.ContainsKey("PUBLIC_DOMAIN")) { $envMap["PUBLIC_DOMAIN"] } else { "" }
  if (-not [string]::IsNullOrWhiteSpace($publicDomain)) {
    $escapedDomain = [regex]::Escape($publicDomain)
    if ($nginx -match "server_name\s+([^;\s]+\s+)*$escapedDomain([\s;]|$)") {
      Ok "nginx server_name includes PUBLIC_DOMAIN=$publicDomain"
    } else {
      Err "PUBLIC_DOMAIN is set to $publicDomain, but nginx server_name is not configured for it"
    }
    $corsOrigins = if ($envMap.ContainsKey("CORS_ALLOWED_ORIGINS")) { $envMap["CORS_ALLOWED_ORIGINS"] } else { "" }
    if ([string]::IsNullOrWhiteSpace($corsOrigins)) {
      Warn "CORS_ALLOWED_ORIGINS is empty; this is OK only when frontend and /api are served from the same domain"
    }
  } else {
    Warn "PUBLIC_DOMAIN is empty; set it before binding a real domain"
  }
  if ($nginx -match "(?m)^\s*listen\s+443\s+ssl") {
    Ok "nginx HTTPS server block is enabled"
    if ((Test-Path "certs/fullchain.pem") -and (Test-Path "certs/privkey.pem")) {
      Ok "nginx TLS certificates exist in deploy/certs"
    } else {
      Err "nginx HTTPS is enabled, but certs/fullchain.pem or certs/privkey.pem is missing"
    }
  }
  else { Warn "nginx HTTPS block is not enabled; public production must terminate HTTPS at Nginx, SLB, CDN, or WAF" }
}

$prodYaml = "..\src\main\resources\application-prod.yaml"
if (Test-Path $prodYaml) {
  $prod = Get-Content -Raw $prodYaml
  if ($prod -match "sk-[A-Za-z0-9_.-]{20,}|password: [^`$({]") {
    Err "application-prod.yaml may contain hard-coded secrets"
  } else {
    Ok "application-prod.yaml has no obvious hard-coded secret"
  }
}

if ($failed) {
  Write-Host "Preflight failed. Fix the errors above before production deploy." -ForegroundColor Red
  exit 1
}

Write-Host "Preflight passed." -ForegroundColor Green
