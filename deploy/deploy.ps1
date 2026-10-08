# =============================================================================
# 知语 AI · 一键部署脚本（Windows PowerShell）
#
# 用法（在 deploy 目录下）：
#   .\deploy.ps1            # 启动 / 更新
#   .\deploy.ps1 down       # 停止并保留数据
#   .\deploy.ps1 logs       # 跟踪应用日志
#   .\deploy.ps1 restart    # 重启
#
# 前提：已安装 Docker Desktop（含 compose v2）。
# =============================================================================
param([string]$Action = "up")

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$ComposeFile = "docker-compose.deploy.yml"
$EnvFile = ".env"

# ---- compose 命令探测 ----
function Get-Compose {
  try { docker compose version | Out-Null; return "docker compose" } catch {}
  if (Get-Command docker-compose -ErrorAction SilentlyContinue) { return "docker-compose" }
  Write-Host "X 未找到 docker compose，请先安装 Docker Desktop。" -ForegroundColor Red
  exit 1
}
$DC = Get-Compose

function Invoke-DC { param([string[]]$Args) & cmd /c "$DC -f $ComposeFile $($Args -join ' ')" }

# ---- 随机串生成 ----
function New-Rand([int]$n = 32) {
  $bytes = New-Object 'System.Byte[]' ([int][math]::Ceiling($n * 1.5))
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  ([Convert]::ToBase64String($bytes) -replace '[^A-Za-z0-9]', '').Substring(0, $n)
}

switch ($Action) {
  "down"    { Invoke-DC @("down"); exit $LASTEXITCODE }
  "restart" { Invoke-DC @("restart"); exit $LASTEXITCODE }
  "logs"    { Invoke-DC @("logs", "-f", "app"); exit $LASTEXITCODE }
  "ps"      { Invoke-DC @("ps"); exit $LASTEXITCODE }
  "up"      { }
  default   { Write-Host "用法: .\deploy.ps1 [up|down|restart|logs|ps]"; exit 1 }
}

# ---- 首次生成 .env ----
if (-not (Test-Path $EnvFile)) {
  Write-Host "[*] 首次部署：生成强随机密钥到 $EnvFile ..." -ForegroundColor Cyan
  $jwt = New-Rand 64
  $now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  @"
# 知语 AI 部署配置（本文件含密钥，已被 .gitignore 排除，切勿提交）
# 生成时间：$now

# ===== 必填：阿里云百炼 API Key（对话/向量/语音共用）=====
DASHSCOPE_API_KEY=

# ===== 对外端口（默认 80；如被占用改成 8088 等）=====
HTTP_PORT=80

# ===== 域名配置（接入现有域名时填写，不含 https://）=====
# 例：PUBLIC_DOMAIN=ai.example.com
PUBLIC_DOMAIN=app.noweyai.com

# 同域部署留空即可；如果前端和 API 分不同域名，填前端完整 origin
# 例：CORS_ALLOWED_ORIGINS=https://www.example.com,https://example.com
CORS_ALLOWED_ORIGINS=

# ===== JVM 内存（演示/低配建议 50%，生产可调回 75%）=====
JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError

# ===== 以下为自动生成的强随机密钥，无需修改 =====
JWT_SECRET=$jwt

MYSQL_DB=zwskchat
MYSQL_USER=zwsk
MYSQL_PASSWORD=$(New-Rand 28)
MYSQL_ROOT_PASSWORD=$(New-Rand 28)

REDIS_PASSWORD=$(New-Rand 28)

RABBIT_USER=zwsk
RABBIT_PASSWORD=$(New-Rand 28)

QDRANT_API_KEY=$(New-Rand 28)

MINIO_ACCESS_KEY=$(New-Rand 20)
MINIO_SECRET_KEY=$(New-Rand 40)
MINIO_BUCKET=zwsk-kb

APP_VERSION=latest
"@ | Set-Content -Path $EnvFile -Encoding UTF8
  Write-Host "[OK] 已生成 $EnvFile" -ForegroundColor Green
  Write-Host ""
  Write-Host "[!] 请编辑 $EnvFile 填入 DASHSCOPE_API_KEY 后，重新运行 .\deploy.ps1" -ForegroundColor Yellow
  exit 0
}

# ---- 校验 DASHSCOPE_API_KEY ----
$envMap = @{}
Get-Content $EnvFile | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') { $envMap[$matches[1].Trim()] = $matches[2].Trim() }
}
if ([string]::IsNullOrWhiteSpace($envMap["DASHSCOPE_API_KEY"])) {
  Write-Host "X $EnvFile 里 DASHSCOPE_API_KEY 还是空的，请填入后再运行。" -ForegroundColor Red
  exit 1
}
$httpPort = if ($envMap["HTTP_PORT"]) { $envMap["HTTP_PORT"] } else { "80" }

Write-Host "[*] 运行生产预检..." -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File .\preflight.ps1 -EnvFile $EnvFile
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "[*] 构建并启动全栈（首次构建后端镜像约需几分钟）..." -ForegroundColor Cyan
Invoke-DC @("up", "-d", "--build")

Write-Host ""
Write-Host "[*] 等待应用就绪（最多 3 分钟）..." -ForegroundColor Cyan
for ($i = 0; $i -lt 36; $i++) {
  try {
    Invoke-RestMethod -Uri "http://127.0.0.1:$httpPort/api/actuator/health/readiness" -TimeoutSec 4 | Out-Null
    Write-Host "[OK] 应用已就绪！" -ForegroundColor Green
    Write-Host ""
    Write-Host "==================================================="
    if ($envMap["PUBLIC_DOMAIN"]) {
      Write-Host "  访问地址： https://$($envMap["PUBLIC_DOMAIN"])/"
    } else {
      Write-Host "  访问地址： http://localhost:$httpPort/"
    }
    Write-Host "  查看日志： .\deploy.ps1 logs"
    Write-Host "==================================================="
    exit 0
  } catch { Start-Sleep -Seconds 5 }
}
Write-Host "[!] 等待超时。用 .\deploy.ps1 logs 查看后端日志排查。" -ForegroundColor Yellow
exit 1
