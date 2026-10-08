# ============================================================
# stop-zwsk.ps1
# Stop Spring Boot. Pass -All to also stop MySQL/Memurai/Qdrant.
# ============================================================
param([switch]$All)

$LogDir = 'D:\conf\zwsk\logs'
$PidFile = Join-Path $LogDir 'app.pid'

if (Test-Path $PidFile) {
    $appPid = Get-Content $PidFile | Select-Object -First 1
    if ($appPid -match '^\d+$') {
        $p = Get-Process -Id $appPid -ErrorAction SilentlyContinue
        if ($null -ne $p) {
            Stop-Process -Id $appPid -Force
            Write-Host "[STOP] Spring Boot pid=$appPid" -ForegroundColor Yellow
        }
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue
}

# Best-effort: kill any java.exe whose command line mentions zwskchat
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -match 'zwskchat'
} | ForEach-Object {
    try {
        Stop-Process -Id $_.ProcessId -Force
        Write-Host "[STOP] java pid=$($_.ProcessId)" -ForegroundColor Yellow
    } catch {}
}

if ($All) {
    Stop-Service Memurai -ErrorAction SilentlyContinue
    Write-Host "[STOP] Memurai" -ForegroundColor Yellow
    Stop-Service ZwskMySQL -ErrorAction SilentlyContinue
    Write-Host "[STOP] ZwskMySQL" -ForegroundColor Yellow
    Get-Process -Name 'qdrant' -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "[STOP] Qdrant" -ForegroundColor Yellow
}
