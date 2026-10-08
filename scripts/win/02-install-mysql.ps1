# ============================================================
# 02-install-mysql.ps1
# 在 D:\conf\mysql 下原生安装 MySQL 8.4 LTS（zip 解压方式），
# 注册为 Windows 服务 ZwskMySQL，开机自启。
#
# ⚠️ 必须以管理员身份运行 PowerShell：
#       右键 PowerShell → Run as administrator
# ============================================================
$ErrorActionPreference = 'Stop'

$MySqlVersion = '8.4.6'
$MySqlBase    = 'D:\conf\mysql'
$MySqlBinDir  = "$MySqlBase\bin"
$MySqlData    = "$MySqlBase\data"
$MySqlLog     = "$MySqlBase\log"
$MyIni        = "$MySqlBase\my.ini"
$ServiceName  = 'ZwskMySQL'
$RootPassword = if ($env:MYSQL_ROOT_PASSWORD) {
    $env:MYSQL_ROOT_PASSWORD
} else {
    $secure = Read-Host '请输入 MySQL root 密码' -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

# 1. 管理员检查
$IsAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $IsAdmin) {
    Write-Error "请以管理员身份运行此脚本（mysqld --install 需要管理员）"
    exit 1
}

# 2. 已存在则跳过
if (Test-Path $MySqlBinDir) {
    Write-Host "[OK] MySQL 已存在于 $MySqlBase，跳过下载/解压" -ForegroundColor DarkGray
} else {
    $Url = "https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MySqlVersion-winx64.zip"
    $Zip = "$env:TEMP\mysql-$MySqlVersion-winx64.zip"
    Write-Host "[DOWNLOAD] $Url" -ForegroundColor Cyan
    Invoke-WebRequest -Uri $Url -OutFile $Zip
    Write-Host "[EXTRACT]  $Zip -> $MySqlBase" -ForegroundColor Cyan
    $TmpExtract = "$env:TEMP\mysql-extract-$([Guid]::NewGuid().ToString('N'))"
    Expand-Archive -Path $Zip -DestinationPath $TmpExtract -Force
    $Inner = Get-ChildItem $TmpExtract | Where-Object { $_.PsIsContainer } | Select-Object -First 1
    if ($null -eq $Inner) { Write-Error "解压结构异常：$TmpExtract"; exit 1 }
    Move-Item -Path "$($Inner.FullName)\*" -Destination $MySqlBase -Force
    Remove-Item -Recurse -Force $TmpExtract, $Zip
    Write-Host "[DONE]     MySQL 已解压到 $MySqlBase" -ForegroundColor Green
}

# 3. 写 my.ini（仅当不存在）
if (-not (Test-Path $MyIni)) {
@"
[mysqld]
basedir=$($MySqlBase -replace '\\','/')
datadir=$($MySqlData -replace '\\','/')
log-error=$($MySqlLog -replace '\\','/')/error.log
slow_query_log=1
slow_query_log_file=$($MySqlLog -replace '\\','/')/slow.log
long_query_time=2
port=3306
bind-address=127.0.0.1
character-set-server=utf8mb4
collation-server=utf8mb4_general_ci
default-time-zone='+08:00'
max_connections=200
default-authentication-plugin=mysql_native_password
skip-name-resolve
log_bin=$($MySqlLog -replace '\\','/')/binlog
binlog_expire_logs_seconds=259200
sql_mode='STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO'

[client]
default-character-set=utf8mb4
port=3306
"@ | Out-File -FilePath $MyIni -Encoding ASCII
    Write-Host "[CREATE]   $MyIni" -ForegroundColor Green
} else {
    Write-Host "[OK]       $MyIni 已存在" -ForegroundColor DarkGray
}

# 4. 初始化数据目录（仅在 data 为空时）
$DataExists = (Get-ChildItem $MySqlData -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0
if (-not $DataExists) {
    Write-Host "[INIT]     初始化 MySQL 数据目录..." -ForegroundColor Cyan
    & "$MySqlBinDir\mysqld.exe" --defaults-file="$MyIni" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) { Write-Error "MySQL --initialize 失败"; exit 1 }
    Write-Host "[DONE]     数据目录初始化完成（root 无密码，下面会改）" -ForegroundColor Green
} else {
    Write-Host "[OK]       MySQL 数据目录非空，跳过初始化" -ForegroundColor DarkGray
}

# 5. 注册并启动 Windows 服务
$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    Write-Host "[SERVICE]  注册 Windows 服务 $ServiceName" -ForegroundColor Cyan
    & "$MySqlBinDir\mysqld.exe" --install $ServiceName --defaults-file="$MyIni"
    if ($LASTEXITCODE -ne 0) { Write-Error "mysqld --install 失败"; exit 1 }
    Set-Service -Name $ServiceName -StartupType Automatic
} else {
    Write-Host "[OK]       服务 $ServiceName 已存在" -ForegroundColor DarkGray
}

$svc = Get-Service -Name $ServiceName
if ($svc.Status -ne 'Running') {
    Start-Service $ServiceName
    Write-Host "[START]    $ServiceName" -ForegroundColor Green
} else {
    Write-Host "[OK]       $ServiceName 已在运行" -ForegroundColor DarkGray
}

# 6. 等待端口可用 + 设置 root 密码 + 创建 zwsk 业务用户/库
Start-Sleep -Seconds 3
$Ready = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        & "$MySqlBinDir\mysql.exe" -uroot --skip-password -e "SELECT 1;" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $Ready = $true; break }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $Ready) {
    # 也许 root 已有密码（重复运行场景）
    Write-Warning "root 无密码登录失败，可能已设置密码。如需重置可手动操作。"
} else {
    Write-Host "[ROOT]     设置 root@localhost 密码..." -ForegroundColor Cyan
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '$RootPassword';"
    & "$MySqlBinDir\mysql.exe" -uroot --skip-password -e $sql
    Write-Host "[ROOT]     root 密码：$RootPassword（请妥善保存到 D:\conf\zwsk\zwsk-secret.yaml）" -ForegroundColor Yellow
}

Write-Host "`n下一步：运行 03-install-redis.ps1" -ForegroundColor Cyan
Write-Host "下下步：运行 05-init-mysql-db.ps1（创建 zwskchat 库 + zwsk 用户 + 导入 schema）" -ForegroundColor Cyan
