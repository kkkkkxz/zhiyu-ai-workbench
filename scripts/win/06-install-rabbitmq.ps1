# ============================================================
# 06-install-rabbitmq.ps1
# Install Erlang/OTP and RabbitMQ Server from official installers,
# register Windows service, create business user `zwsk`.
#
# !!! REQUIRES ADMIN PowerShell (NSIS install + service registration) !!!
# ============================================================
$ErrorActionPreference = 'Stop'

$IsAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $IsAdmin) {
    Write-Error "Run this script as Administrator (right-click PowerShell -> Run as administrator)."
    exit 1
}

# Erlang OTP 27.3.3 — broadly compatible with RabbitMQ 4.0.x and 4.1.x.
$ErlVersion       = '27.3.3'
$ErlInstallerUrl  = "https://github.com/erlang/otp/releases/download/OTP-$ErlVersion/otp_win64_$ErlVersion.exe"
$ErlInstallerPath = "$env:TEMP\otp_win64_$ErlVersion.exe"

$RmqVersion       = '4.0.5'
$RmqInstallerUrl  = "https://github.com/rabbitmq/rabbitmq-server/releases/download/v$RmqVersion/rabbitmq-server-$RmqVersion.exe"
$RmqInstallerPath = "$env:TEMP\rabbitmq-server-$RmqVersion.exe"

$ZwskUser    = if ($env:ZWSK_RABBIT_USER) { $env:ZWSK_RABBIT_USER } else { 'zwsk' }
$ZwskPassword = if ($env:ZWSK_RABBIT_PASSWORD) {
    $env:ZWSK_RABBIT_PASSWORD
} else {
    $secure = Read-Host 'Enter a RabbitMQ password for the zwsk user' -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

# ---------------------------------------------------------------------
# 1. Erlang/OTP via official NSIS installer
# ---------------------------------------------------------------------
$erlInstalled = $false
$existingErl = Get-Command erl.exe -ErrorAction SilentlyContinue
if ($null -ne $existingErl) { $erlInstalled = $true }
if (-not $erlInstalled -and $env:ERLANG_HOME -and (Test-Path "$env:ERLANG_HOME\bin\erl.exe")) {
    $erlInstalled = $true
}
if (-not $erlInstalled) {
    foreach ($p in 'C:\Program Files\Erlang OTP\bin\erl.exe', 'C:\Program Files\erl-*\bin\erl.exe') {
        $found = Get-ChildItem $p -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $erlInstalled = $true; break }
    }
}

if (-not $erlInstalled) {
    if (-not (Test-Path $ErlInstallerPath)) {
        Write-Host "[DOWNLOAD] $ErlInstallerUrl" -ForegroundColor Cyan
        Invoke-WebRequest -Uri $ErlInstallerUrl -OutFile $ErlInstallerPath
    }
    Write-Host "[INSTALL]  Erlang OTP $ErlVersion (silent)" -ForegroundColor Cyan
    Start-Process -FilePath $ErlInstallerPath -ArgumentList '/S' -Wait -NoNewWindow
    Start-Sleep -Seconds 2
} else {
    Write-Host "[OK]     Erlang already installed" -ForegroundColor DarkGray
}

# Locate Erlang installation and set ERLANG_HOME (system-wide)
$erlangBase = $null
foreach ($cand in @(
    "C:\Program Files\Erlang OTP",
    "C:\Program Files\erl-$ErlVersion",
    "C:\Program Files\erl$ErlVersion",
    "C:\Program Files (x86)\Erlang OTP")) {
    if (Test-Path "$cand\bin\erl.exe") { $erlangBase = $cand; break }
}
if (-not $erlangBase) {
    $candidate = Get-ChildItem 'C:\Program Files' -Directory -Filter 'erl*' -ErrorAction SilentlyContinue |
                 Where-Object { Test-Path "$($_.FullName)\bin\erl.exe" } | Select-Object -First 1
    if ($candidate) { $erlangBase = $candidate.FullName }
}
if (-not $erlangBase) {
    Write-Error "Cannot locate Erlang installation directory. Install manually from https://www.erlang.org/downloads"
    exit 1
}
[System.Environment]::SetEnvironmentVariable('ERLANG_HOME', $erlangBase, 'Machine')
$env:ERLANG_HOME = $erlangBase
Write-Host "[OK]     ERLANG_HOME=$erlangBase" -ForegroundColor DarkGray

# ---------------------------------------------------------------------
# 2. RabbitMQ Server via official .exe (NSIS silent install)
# ---------------------------------------------------------------------
$svc = Get-Service -Name 'RabbitMQ' -ErrorAction SilentlyContinue
if ($null -ne $svc) {
    Write-Host "[OK]     RabbitMQ service exists" -ForegroundColor DarkGray
} else {
    if (-not (Test-Path $RmqInstallerPath)) {
        Write-Host "[DOWNLOAD] $RmqInstallerUrl" -ForegroundColor Cyan
        Invoke-WebRequest -Uri $RmqInstallerUrl -OutFile $RmqInstallerPath
    }
    Write-Host "[INSTALL]  $RmqInstallerPath /S (silent)" -ForegroundColor Cyan
    Start-Process -FilePath $RmqInstallerPath -ArgumentList '/S' -Wait -NoNewWindow
    Start-Sleep -Seconds 5
    $svc = Get-Service -Name 'RabbitMQ' -ErrorAction SilentlyContinue
    if ($null -eq $svc) {
        Write-Error "RabbitMQ service not registered after install. Check installer log."
        exit 1
    }
}
Set-Service -Name 'RabbitMQ' -StartupType Automatic
if ($svc.Status -ne 'Running') { Start-Service RabbitMQ; Start-Sleep -Seconds 8 }
Write-Host "[OK]     RabbitMQ service running" -ForegroundColor Green

# ---------------------------------------------------------------------
# 3. Locate rabbitmqctl.bat / rabbitmq-plugins.bat
# ---------------------------------------------------------------------
$baseDirs = @(
    'C:\Program Files\RabbitMQ Server',
    'C:\Program Files (x86)\RabbitMQ Server',
    'D:\Program Files\RabbitMQ Server'
)
$ctl = $null; $plugins = $null
foreach ($base in $baseDirs) {
    if (Test-Path $base) {
        $ctlCandidates = Get-ChildItem $base -Recurse -Filter 'rabbitmqctl.bat' -ErrorAction SilentlyContinue
        if ($ctlCandidates) { $ctl = $ctlCandidates[0].FullName }
        $plgCandidates = Get-ChildItem $base -Recurse -Filter 'rabbitmq-plugins.bat' -ErrorAction SilentlyContinue
        if ($plgCandidates) { $plugins = $plgCandidates[0].FullName }
        if ($ctl -and $plugins) { break }
    }
}
if (-not $ctl -or -not $plugins) {
    Write-Warning "rabbitmqctl.bat / rabbitmq-plugins.bat not found; user setup skipped."
    Write-Host "Find them under 'C:\Program Files\RabbitMQ Server\rabbitmq_server-x.y.z\sbin\' and run manually." -ForegroundColor Yellow
    exit 0
}
Write-Host "[OK]     rabbitmqctl   = $ctl" -ForegroundColor DarkGray
Write-Host "[OK]     rabbitmq-plug = $plugins" -ForegroundColor DarkGray

# ---------------------------------------------------------------------
# 4. Enable management plugin (HTTP UI on :15672)
# ---------------------------------------------------------------------
Write-Host "[PLUGIN] enable rabbitmq_management" -ForegroundColor Cyan
& $plugins enable rabbitmq_management 2>&1 | Out-Host

# ---------------------------------------------------------------------
# 5. Create app user `zwsk` (admin tag) and grant on / vhost
# ---------------------------------------------------------------------
Write-Host "[USER]   ensure user '$ZwskUser'" -ForegroundColor Cyan
$null = & $ctl list_users 2>&1
& $ctl add_user $ZwskUser $ZwskPassword 2>&1 | Out-Host
& $ctl change_password $ZwskUser $ZwskPassword 2>&1 | Out-Host
& $ctl set_user_tags $ZwskUser administrator 2>&1 | Out-Host
& $ctl set_permissions -p / $ZwskUser '.*' '.*' '.*' 2>&1 | Out-Host

# Optional: keep guest available only on localhost (default RabbitMQ behavior)
Write-Host "[OK]     RabbitMQ 4.x ready" -ForegroundColor Green
Write-Host "         AMQP   :5672   user=$ZwskUser (password supplied outside the repository)"  -ForegroundColor Cyan
Write-Host "         HTTP UI:15672  http://127.0.0.1:15672  (login with $ZwskUser)" -ForegroundColor Cyan
