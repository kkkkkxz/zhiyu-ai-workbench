# ============================================================
# 10-register-autostart.ps1
# Register a per-user scheduled task to start the Spring Boot app
# at user logon. Task name: ZwskChatApp. No admin needed.
# ============================================================
$ErrorActionPreference = 'Stop'

$TaskName = 'ZwskChatApp'
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$StartPs1 = Join-Path $PSScriptRoot 'start-zwsk.ps1'

if (-not (Test-Path $StartPs1)) {
    Write-Error "Cannot find $StartPs1"
    exit 1
}

$existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    Write-Host "[OK] Task $TaskName exists; re-creating" -ForegroundColor DarkGray
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
}

$cmd = "powershell.exe"
$arg = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$StartPs1`""

$action    = New-ScheduledTaskAction -Execute $cmd -Argument $arg -WorkingDirectory $RepoRoot
$trigger   = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
$settings  = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero) -Hidden

Register-ScheduledTask -TaskName $TaskName `
    -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
    -Description 'Zwsk AI - Spring Boot backend autostart at logon' | Out-Null

Write-Host "[REGISTER] Task $TaskName registered (AtLogOn)" -ForegroundColor Green
Write-Host "Run now:  Start-ScheduledTask -TaskName $TaskName" -ForegroundColor Cyan
