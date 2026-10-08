# ============================================================
# 03-install-redis.ps1
# 安装 Memurai Developer（Windows 原生 Redis 7 兼容实现，Redis 官方合作方），
# 默认监听 127.0.0.1:6379，注册为 Windows 服务自动启动。
#
# 优先用 winget 安装；如系统无 winget 则给出 .msi 下载链接，让用户手动安装。
# ============================================================
$ErrorActionPreference = 'Stop'

# 1. 已有则跳过
$svc = Get-Service -Name 'Memurai' -ErrorAction SilentlyContinue
if ($null -ne $svc) {
    Write-Host "[OK] Memurai 服务已存在" -ForegroundColor DarkGray
    if ($svc.Status -ne 'Running') {
        Start-Service Memurai
        Write-Host "[START] Memurai" -ForegroundColor Green
    }
    Write-Host "[CHECK] 端口 6379 探测..." -ForegroundColor Cyan
    Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -InformationLevel Quiet
    return
}

# 2. winget 检测
$WingetOk = $false
try {
    winget --version 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $WingetOk = $true }
} catch {}

if ($WingetOk) {
    Write-Host "[WINGET] 安装 Memurai.MemuraiDeveloper（自动接受协议）..." -ForegroundColor Cyan
    winget install --id Memurai.MemuraiDeveloper --silent --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "winget 安装失败（exit=$LASTEXITCODE）。改走手动下载流程。"
        $WingetOk = $false
    }
}

if (-not $WingetOk) {
    Write-Host "请手动下载并安装 Memurai Developer（免费）：" -ForegroundColor Yellow
    Write-Host "  https://www.memurai.com/get-memurai" -ForegroundColor Yellow
    Write-Host "下载 .msi 后双击安装，下一步默认即可。安装完成后重新运行本脚本。" -ForegroundColor Yellow
    return
}

# 3. 启动
Start-Sleep -Seconds 3
$svc = Get-Service -Name 'Memurai' -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    Write-Warning "未发现 Memurai 服务；请检查安装日志或重启 PowerShell 后重试。"
    return
}
Set-Service -Name 'Memurai' -StartupType Automatic
if ($svc.Status -ne 'Running') { Start-Service Memurai }
Write-Host "[OK] Memurai 服务运行中" -ForegroundColor Green

# 4. 验证 PING
Start-Sleep -Seconds 2
$cli = Get-Command memurai-cli.exe -ErrorAction SilentlyContinue
if ($null -ne $cli) {
    & $cli.Source PING
} else {
    Write-Host "[INFO] 未在 PATH 中找到 memurai-cli.exe；可在安装目录里找到。" -ForegroundColor DarkGray
}
Write-Host "`n下一步：运行 04-install-qdrant.ps1" -ForegroundColor Cyan
