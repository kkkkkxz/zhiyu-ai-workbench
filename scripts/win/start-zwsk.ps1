# ============================================================
# start-zwsk.ps1
# Bring up the backend stack:
#   1) Start local middleware: MySQL, Redis, Qdrant, RabbitMQ, MinIO
#   2) Run mvnw -DskipTests spring-boot:run in background
#
# Logs to D:\conf\zwsk\logs\app.log; pid file at app.pid.
# ============================================================
[CmdletBinding()]
param(
    [switch]$SkipMiddleware,
    [switch]$StrictMiddleware
)

$ErrorActionPreference = 'Continue'

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$LogDir   = 'D:\conf\zwsk\logs'
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }
$AppLog = Join-Path $LogDir 'app.log'
$PidFile = Join-Path $LogDir 'app.pid'
$BackendPort = 8080

function Get-PortListeners([int]$Port) {
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    $result = @()
    foreach ($listener in $listeners) {
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
        $result += [PSCustomObject]@{
            Port        = $Port
            ProcessId   = $listener.OwningProcess
            Name        = $processInfo.Name
            CommandLine = $processInfo.CommandLine
        }
    }
    return $result
}

function Test-ZwskBackendProcess($processInfo) {
    if ($null -eq $processInfo) { return $false }
    $cmd = [string]$processInfo.CommandLine
    if ([string]::IsNullOrWhiteSpace($cmd)) { return $false }
    return ($cmd -match 'zwskchat' -or $cmd -match 'ZwskchatApplication' -or $cmd -match 'spring-boot:run')
}

if (-not $SkipMiddleware) {
    Write-Host "==> 1. Start middleware before backend" -ForegroundColor Cyan
    $middlewareScript = Join-Path $PSScriptRoot 'start-middleware.ps1'
    if (-not (Test-Path $middlewareScript)) {
        Write-Error "Middleware script not found: $middlewareScript"
        exit 1
    }

    $middlewareArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $middlewareScript)
    if ($StrictMiddleware) { $middlewareArgs += '-Strict' }
    & powershell.exe @middlewareArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Middleware startup failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
} else {
    Write-Host "==> 1. Skip middleware startup" -ForegroundColor DarkYellow
}

Write-Host "==> 2. Launch Spring Boot (log: $AppLog)" -ForegroundColor Cyan
Set-Location $RepoRoot
$mvnw = Join-Path $RepoRoot 'mvnw.cmd'
if (-not (Test-Path $mvnw)) {
    Write-Error "mvnw.cmd not found at $RepoRoot"
    exit 1
}

$portOwners = @(Get-PortListeners $BackendPort)
if ($portOwners.Count -gt 0) {
    Write-Host "[WARN] Port $BackendPort is already in use:" -ForegroundColor Yellow
    foreach ($owner in $portOwners) {
        Write-Host "       PID=$($owner.ProcessId) Process=$($owner.Name)" -ForegroundColor Yellow
        Write-Host "       CommandLine=$($owner.CommandLine)" -ForegroundColor DarkYellow
    }

    $isOwnBackend = $true
    foreach ($owner in $portOwners) {
        if (-not (Test-ZwskBackendProcess $owner)) {
            $isOwnBackend = $false
            break
        }
    }

    if ($isOwnBackend) {
        Write-Host "[OK] Spring Boot backend already appears to be running on port $BackendPort; skip duplicate startup." -ForegroundColor Green
        Write-Host "     Log: Get-Content `"$AppLog`" -Wait" -ForegroundColor DarkGray
        $portOwners[0].ProcessId | Out-File -FilePath $PidFile -Encoding ASCII
        exit 0
    }

    Write-Error "Port $BackendPort is occupied by a non-zwsk process. Stop it or change server.port before starting backend."
    exit 1
}

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = "/c `"cd /d `"$RepoRoot`" && `"$mvnw`" -DskipTests spring-boot:run >> `"$AppLog`" 2>&1`""
$startInfo.WorkingDirectory = $RepoRoot
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$proc = [System.Diagnostics.Process]::Start($startInfo)

Write-Host "[OK] Spring Boot launched pid=$($proc.Id)" -ForegroundColor Green
Write-Host "    Stop:  Stop-Process -Id $($proc.Id) -Force   (or run stop-zwsk.ps1)" -ForegroundColor DarkGray
Write-Host "    Log:   Get-Content `"$AppLog`" -Wait" -ForegroundColor DarkGray
$proc.Id | Out-File -FilePath $PidFile -Encoding ASCII
