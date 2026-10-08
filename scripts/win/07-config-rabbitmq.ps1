# ============================================================
# 07-config-rabbitmq.ps1
# Restart RabbitMQ to activate plugins, then create user `zwsk`.
# Run as Administrator.
# ============================================================
$ErrorActionPreference = 'Continue'

$IsAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $IsAdmin) {
    Write-Error "Run this script as Administrator."
    exit 1
}

if (-not $env:ERLANG_HOME) {
    $env:ERLANG_HOME = [System.Environment]::GetEnvironmentVariable('ERLANG_HOME', 'Machine')
}
if (-not $env:ERLANG_HOME) { $env:ERLANG_HOME = 'C:\Program Files\Erlang OTP' }
Write-Host "[OK] ERLANG_HOME=$env:ERLANG_HOME" -ForegroundColor DarkGray

$ctl = Get-ChildItem 'C:\Program Files\RabbitMQ Server' -Recurse -Filter 'rabbitmqctl.bat' -ErrorAction SilentlyContinue |
       Select-Object -First 1 -ExpandProperty FullName
$plg = Get-ChildItem 'C:\Program Files\RabbitMQ Server' -Recurse -Filter 'rabbitmq-plugins.bat' -ErrorAction SilentlyContinue |
       Select-Object -First 1 -ExpandProperty FullName
if (-not $ctl) { Write-Error "rabbitmqctl.bat not found"; exit 1 }
Write-Host "[OK] ctl = $ctl" -ForegroundColor DarkGray

# 1. Restart RabbitMQ service so the previously-enabled plugins activate
Write-Host "==> Restarting RabbitMQ service" -ForegroundColor Cyan
Restart-Service -Name 'RabbitMQ' -Force
Start-Sleep -Seconds 10
$svc = Get-Service RabbitMQ
Write-Host "[OK] service status: $($svc.Status)" -ForegroundColor Green

# 2. Wait for the broker to actually be ready (rabbitmqctl status)
$ready = $false
for ($i=0; $i -lt 20; $i++) {
    & $ctl status 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    Write-Warning "Broker did not become ready in 40s; continuing anyway"
}

# 3. Ensure plugins enabled (idempotent)
& $plg enable rabbitmq_management 2>&1 | Out-Host

# 4. Create / update user `zwsk`
$ZwskUser     = if ($env:ZWSK_RABBIT_USER) { $env:ZWSK_RABBIT_USER } else { 'zwsk' }
$ZwskPassword = if ($env:ZWSK_RABBIT_PASSWORD) {
    $env:ZWSK_RABBIT_PASSWORD
} else {
    $secure = Read-Host 'Enter a RabbitMQ password for the zwsk user' -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

Write-Host "==> Ensure user '$ZwskUser'" -ForegroundColor Cyan
$users = & $ctl list_users 2>&1
if ($users -match "^$ZwskUser\b") {
    Write-Host "[OK] user '$ZwskUser' already exists -> updating password" -ForegroundColor DarkGray
    & $ctl change_password $ZwskUser $ZwskPassword 2>&1 | Out-Host
} else {
    & $ctl add_user $ZwskUser $ZwskPassword 2>&1 | Out-Host
}
& $ctl set_user_tags $ZwskUser administrator 2>&1 | Out-Host
& $ctl set_permissions -p / $ZwskUser '.*' '.*' '.*' 2>&1 | Out-Host

Write-Host "[OK] RabbitMQ ready" -ForegroundColor Green
Write-Host "     AMQP    : 127.0.0.1:5672   user=$ZwskUser (password supplied outside the repository)"  -ForegroundColor Cyan
Write-Host "     HTTP UI : http://127.0.0.1:15672  (login with $ZwskUser)" -ForegroundColor Cyan

Write-Host ""
Write-Host "Verify in app:" -ForegroundColor Yellow
Write-Host "  Re-enable rabbit health check by removing management.health.rabbit.enabled=false" -ForegroundColor Yellow
