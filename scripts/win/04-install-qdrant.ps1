# ============================================================
# 04-install-qdrant.ps1
# Download Qdrant Windows native binary to D:\conf\qdrant\bin,
# write config.yaml, register a Scheduled Task triggered AtLogOn.
# Runs as current user, no admin needed.
# ============================================================
$ErrorActionPreference = 'Stop'

$QdrantVersion = '1.13.0'
$QdrantBase    = 'D:\conf\qdrant'
$QdrantBin     = "$QdrantBase\bin"
$QdrantExe     = "$QdrantBin\qdrant.exe"
$QdrantConfig  = "$QdrantBase\config.yaml"
$QdrantStorage = "$QdrantBase\storage"
$QdrantSnap    = "$QdrantBase\snapshots"
$TaskName      = 'ZwskQdrant'

# 1. Download + extract
if (-not (Test-Path $QdrantExe)) {
    $Url = "https://github.com/qdrant/qdrant/releases/download/v$QdrantVersion/qdrant-x86_64-pc-windows-msvc.zip"
    $Zip = "$env:TEMP\qdrant-$QdrantVersion-windows.zip"
    Write-Host "[DOWNLOAD] $Url" -ForegroundColor Cyan
    Invoke-WebRequest -Uri $Url -OutFile $Zip
    if (-not (Test-Path $QdrantBin)) { New-Item -ItemType Directory -Path $QdrantBin | Out-Null }
    Write-Host "[EXTRACT]  $Zip -> $QdrantBin" -ForegroundColor Cyan
    Expand-Archive -Path $Zip -DestinationPath $QdrantBin -Force
    Remove-Item $Zip
    if (-not (Test-Path $QdrantExe)) {
        Write-Error "qdrant.exe not found after extract: $QdrantBin"
        exit 1
    }
} else {
    Write-Host "[OK] $QdrantExe exists" -ForegroundColor DarkGray
}

# 2. Write config.yaml
if (-not (Test-Path $QdrantConfig)) {
@"
log_level: INFO
storage:
  storage_path: $($QdrantStorage -replace '\\','/')
  snapshots_path: $($QdrantSnap -replace '\\','/')
  performance:
    max_search_threads: 0
service:
  host: 127.0.0.1
  http_port: 6333
  grpc_port: 6334
  enable_cors: true
"@ | Out-File -FilePath $QdrantConfig -Encoding UTF8
    Write-Host "[CREATE]   $QdrantConfig" -ForegroundColor Green
} else {
    Write-Host "[OK]       $QdrantConfig exists" -ForegroundColor DarkGray
}

# 3. Generate start.bat
$StartBat = "$QdrantBase\start-qdrant.bat"
@"
@echo off
cd /d "$QdrantBase"
"$QdrantExe" --config-path "$QdrantConfig" >> "$QdrantBase\qdrant.log" 2>&1
"@ | Out-File -FilePath $StartBat -Encoding ASCII
Write-Host "[CREATE]   $StartBat" -ForegroundColor Green

# 4. Register scheduled task (AtLogOn)
$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    Write-Host "[OK]       Task $TaskName exists" -ForegroundColor DarkGray
} else {
    $action    = New-ScheduledTaskAction -Execute "$StartBat"
    $trigger   = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
    $settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                  -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero)
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
        -Principal $principal -Settings $settings `
        -Description 'Zwsk AI - Qdrant vector DB autostart' | Out-Null
    Write-Host "[REGISTER] Task $TaskName registered (AtLogOn)" -ForegroundColor Green
}

# 5. Start now
$running = Get-Process -Name 'qdrant' -ErrorAction SilentlyContinue
if ($null -eq $running) {
    Start-ScheduledTask -TaskName $TaskName
    Write-Host "[START]    Qdrant started via scheduled task" -ForegroundColor Green
    Start-Sleep -Seconds 4
} else {
    Write-Host "[OK]       qdrant process already running" -ForegroundColor DarkGray
}

# 6. Probe HTTP port 6333
try {
    $r = Invoke-WebRequest "http://127.0.0.1:6333/" -TimeoutSec 5 -UseBasicParsing
    Write-Host "[OK]       Qdrant HTTP 6333 ready: $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Warning "Qdrant port not ready yet. Check log: $QdrantBase\qdrant.log"
}

Write-Host ""
Write-Host "Next: run 05-init-mysql-db.ps1" -ForegroundColor Cyan
