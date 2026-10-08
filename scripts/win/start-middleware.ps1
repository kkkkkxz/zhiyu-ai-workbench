# ============================================================
# start-middleware.ps1
# Start local middleware required by the zwskchat backend:
#   MySQL, Redis, Qdrant, RabbitMQ, MinIO
#
# Redis is started with D:\conf\zwsk\redis\redis-zwsk.conf when available.
# Qdrant and MinIO are started via scheduled tasks created by install scripts.
# ============================================================
[CmdletBinding()]
param(
    [switch]$Strict,
    [int]$WaitSeconds = 20
)

$ErrorActionPreference = 'Continue'

$RedisDir  = 'D:\conf\zwsk\redis'
$RedisConf = Join-Path $RedisDir 'redis-zwsk.conf'
$RedisServerCandidates = @(
    'D:\devlopment\redis\redis-server.exe',
    'D:\development\redis\redis-server.exe',
    'redis-server.exe'
)
$RedisCliCandidates = @(
    'D:\devlopment\redis\redis-cli.exe',
    'D:\development\redis\redis-cli.exe',
    'redis-cli.exe'
)

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Find-Executable([string[]]$Candidates) {
    foreach ($candidate in $Candidates) {
        if (Test-Path $candidate) { return $candidate }
        $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    return $null
}

function Test-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutMs = 1000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if ($ok -and $client.Connected) { return $true }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-TcpPort([int]$Port, [string]$Label, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        if (Test-TcpPort '127.0.0.1' $Port 700) {
            Write-Host "[OK]   $Label 127.0.0.1:$Port" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    Write-Warning "$Label 127.0.0.1:$Port not reachable"
    return $false
}

function Ensure-ServiceByName([string]$Name) {
    $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if ($null -eq $svc) { return $false }

    if ($svc.Status -ne 'Running') {
        try {
            Start-Service -Name $Name -ErrorAction Stop
            Start-Sleep -Seconds 2
            $svc = Get-Service -Name $Name -ErrorAction SilentlyContinue
            Write-Host "[START] service $Name => $($svc.Status)" -ForegroundColor Green
        } catch {
            Write-Warning "Failed to start service ${Name}: $($_.Exception.Message)"
        }
    } else {
        Write-Host "[OK]   service $Name already running" -ForegroundColor DarkGray
    }
    return $true
}

function Ensure-AnyService([string[]]$Names, [string]$Label) {
    $found = $false
    foreach ($name in $Names) {
        if (Ensure-ServiceByName $name) { $found = $true }
    }
    if (-not $found) {
        Write-Host "[SKIP] no known $Label Windows service found" -ForegroundColor DarkYellow
    }
}

function Ensure-ScheduledTaskProcess([string]$TaskName, [string]$ProcessName, [string]$Label, [int]$SleepSeconds = 3) {
    $proc = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue
    if ($proc) {
        Write-Host "[OK]   $Label process already running pid=$($proc.Id -join ',')" -ForegroundColor DarkGray
        return
    }

    $task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
    if ($null -eq $task) {
        Write-Host "[SKIP] scheduled task $TaskName not found for $Label" -ForegroundColor DarkYellow
        return
    }

    try {
        Start-ScheduledTask -TaskName $TaskName
        Start-Sleep -Seconds $SleepSeconds
        Write-Host "[START] $Label via scheduled task $TaskName" -ForegroundColor Green
    } catch {
        Write-Warning "Failed to start scheduled task ${TaskName}: $($_.Exception.Message)"
    }
}

function Get-RedisConfigValue([string]$Cli, [string]$Key) {
    try {
        $value = & $Cli -h 127.0.0.1 -p 6379 CONFIG GET $Key 2>$null | Select-Object -Last 1
        return $value
    } catch {
        return $null
    }
}

function Stop-RedisServerProcesses() {
    Get-CimInstance Win32_Process -Filter "Name='redis-server.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        try {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction Stop
            Write-Host "[STOP] redis-server.exe pid=$($_.ProcessId)" -ForegroundColor Yellow
        } catch {
            Write-Warning "Failed to stop redis-server.exe pid=$($_.ProcessId): $($_.Exception.Message)"
        }
    }
}

function Ensure-ZwskRedis() {
    $redisCli = Find-Executable $RedisCliCandidates
    $redisServer = Find-Executable $RedisServerCandidates
    $redisOpen = Test-TcpPort '127.0.0.1' 6379 800
    $needsRestart = $false

    if ($redisOpen -and $redisCli) {
        $dir = Get-RedisConfigValue $redisCli 'dir'
        $save = Get-RedisConfigValue $redisCli 'save'
        $guard = Get-RedisConfigValue $redisCli 'stop-writes-on-bgsave-error'
        Write-Host "[INFO] Redis dir=$dir save='$save' stop-writes-on-bgsave-error=$guard" -ForegroundColor DarkGray

        if ($dir -and (($dir.TrimEnd('\')) -ine $RedisDir)) { $needsRestart = $true }
        if ($save) { $needsRestart = $true }
        if ($guard -and ($guard -ine 'no')) { $needsRestart = $true }
    }

    if ($redisOpen -and $needsRestart) {
        Write-Host "[FIX] Redis is not using zwsk config; restarting with $RedisConf" -ForegroundColor Yellow
        Stop-RedisServerProcesses
        Start-Sleep -Seconds 2
        $redisOpen = $false
    }

    if (-not $redisOpen) {
        if ($redisServer -and (Test-Path $RedisConf)) {
            if (-not (Test-Path $RedisDir)) { New-Item -ItemType Directory -Force -Path $RedisDir | Out-Null }
            Start-Process -FilePath $redisServer -ArgumentList "`"$RedisConf`"" -WindowStyle Hidden
            Start-Sleep -Seconds 2
            Write-Host "[START] Redis: $redisServer $RedisConf" -ForegroundColor Green
        } else {
            Write-Host "[SKIP] Redis server/config not found; trying Memurai fallback" -ForegroundColor DarkYellow
            Ensure-ServiceByName 'Memurai' | Out-Null
        }
    } else {
        Write-Host '[OK]   Redis port already open' -ForegroundColor DarkGray
    }

    $redisOpen = Test-TcpPort '127.0.0.1' 6379 800
    if ($redisOpen -and $redisCli) {
        try {
            & $redisCli -h 127.0.0.1 -p 6379 CONFIG SET stop-writes-on-bgsave-error no | Out-Null
            & $redisCli -h 127.0.0.1 -p 6379 CONFIG SET save "" | Out-Null
            Write-Host '[OK]   Redis write guard relaxed and snapshot save disabled' -ForegroundColor DarkGray
        } catch {
            Write-Host "[SKIP] Redis CONFIG SET failed: $($_.Exception.Message)" -ForegroundColor DarkYellow
        }
    }
}

Write-Step 'Ensure MySQL'
Ensure-AnyService @('ZwskMySQL','MySQL84','MySQL80','MySQL57','MySQL') 'MySQL'

Write-Step 'Ensure Redis'
Ensure-ZwskRedis

Write-Step 'Ensure Qdrant'
Ensure-ScheduledTaskProcess 'ZwskQdrant' 'qdrant' 'Qdrant' 4

Write-Step 'Ensure RabbitMQ'
$rmqStarted = Ensure-ServiceByName 'RabbitMQ'
if (-not $rmqStarted) {
    $rabbitServices = Get-Service | Where-Object { $_.Name -like '*Rabbit*' -or $_.DisplayName -like '*Rabbit*' }
    if ($rabbitServices) {
        foreach ($svc in $rabbitServices) { Ensure-ServiceByName $svc.Name | Out-Null }
    } else {
        Write-Host '[SKIP] RabbitMQ service not found' -ForegroundColor DarkYellow
    }
}

Write-Step 'Ensure MinIO'
Ensure-ScheduledTaskProcess 'ZwskMinio' 'minio' 'MinIO' 4

Write-Step 'Probe middleware ports'
$allOk = $true
$checks = @(
    @{ Port = 3306;  Label = 'MySQL' },
    @{ Port = 6379;  Label = 'Redis' },
    @{ Port = 5672;  Label = 'RabbitMQ AMQP' },
    @{ Port = 15672; Label = 'RabbitMQ UI' },
    @{ Port = 6333;  Label = 'Qdrant HTTP' },
    @{ Port = 6334;  Label = 'Qdrant gRPC' },
    @{ Port = 9000;  Label = 'MinIO S3' },
    @{ Port = 9001;  Label = 'MinIO Console' }
)

foreach ($check in $checks) {
    $ok = Wait-TcpPort $check.Port $check.Label $WaitSeconds
    if (-not $ok) { $allOk = $false }
}

try {
    $qdrant = Invoke-RestMethod -Uri 'http://127.0.0.1:6333/' -TimeoutSec 3
    if ($qdrant.version) { Write-Host "[OK]   Qdrant version: $($qdrant.version)" -ForegroundColor Green }
} catch {
    Write-Host "[SKIP] Qdrant version check: $($_.Exception.Message)" -ForegroundColor DarkYellow
}

if ($Strict -and -not $allOk) {
    Write-Error 'One or more required middleware ports are not reachable.'
    exit 1
}

exit 0
