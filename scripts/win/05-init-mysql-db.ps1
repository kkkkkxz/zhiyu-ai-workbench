# ============================================================
# 05-init-mysql-db.ps1
# Create zwskchat database and import sql/V1.0__init_schema.sql.
# Uses the existing root account. Supply MYSQL_ROOT_PASSWORD before running.
# Tries to auto-detect mysql.exe in PATH or common install dirs.
# ============================================================
$ErrorActionPreference = 'Continue'   # mysql.exe writes harmless warnings to stderr

# --- Parameters (override via env or edit this block) ---
$MySqlHost     = '127.0.0.1'
$MySqlPort     = 3306
$RootUser      = if ($env:MYSQL_ROOT_USER) { $env:MYSQL_ROOT_USER } else { 'root' }
$RootPassword  = if ($env:MYSQL_ROOT_PASSWORD) {
    $env:MYSQL_ROOT_PASSWORD
} else {
    $secure = Read-Host '请输入 MySQL root 密码' -AsSecureString
    [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
$Database      = 'zwskchat'
$RepoRoot      = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$SqlFile       = Join-Path $RepoRoot 'sql\V1.0__init_schema.sql'

# --- Locate mysql.exe ---
$MySqlExe = $null
$cmd = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($null -ne $cmd) { $MySqlExe = $cmd.Source }
if (-not $MySqlExe) {
    $candidates = @(
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe',
        'D:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'D:\conf\mysql\bin\mysql.exe',
        'C:\xampp\mysql\bin\mysql.exe',
        'C:\wamp64\bin\mysql\mysql8.0.31\bin\mysql.exe'
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $MySqlExe = $c; break }
    }
}
if (-not $MySqlExe) {
    Write-Error "mysql.exe not found in PATH or common locations. Set env MYSQL_BIN or install MySQL client."
    exit 1
}
Write-Host "[OK] mysql client: $MySqlExe" -ForegroundColor DarkGray

if (-not (Test-Path $SqlFile)) {
    Write-Error "Schema file not found: $SqlFile"
    exit 1
}

# --- Verify connection ---
Write-Host "[CONN] verifying $RootUser@$MySqlHost`:$MySqlPort ..." -ForegroundColor Cyan
$null = & $MySqlExe -h $MySqlHost -P $MySqlPort -u $RootUser "-p$RootPassword" -e "SELECT VERSION();" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERR] Cannot connect with $RootUser/$RootPassword. Adjust MYSQL_ROOT_PASSWORD env or edit script." -ForegroundColor Red
    exit 1
}

# --- Create database ---
Write-Host "[INIT] CREATE DATABASE IF NOT EXISTS $Database" -ForegroundColor Cyan
$ddl = "CREATE DATABASE IF NOT EXISTS ``$Database`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
$null = & $MySqlExe -h $MySqlHost -P $MySqlPort -u $RootUser "-p$RootPassword" -e $ddl 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "[ERR] CREATE DATABASE failed" -ForegroundColor Red; exit 1 }

# --- Import schema (read file as UTF-8 then pipe) ---
Write-Host "[IMPORT] $SqlFile -> $Database" -ForegroundColor Cyan
$schema = Get-Content $SqlFile -Raw -Encoding UTF8
$null = $schema | & $MySqlExe -h $MySqlHost -P $MySqlPort -u $RootUser "-p$RootPassword" --default-character-set=utf8mb4 $Database 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "[ERR] Schema import failed" -ForegroundColor Red; exit 1 }

# --- Verify ---
$cnt = (& $MySqlExe -h $MySqlHost -P $MySqlPort -u $RootUser "-p$RootPassword" -N -B `
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';" 2>$null) | Select-Object -First 1
Write-Host "[OK] Database $Database initialized. Tables: $cnt" -ForegroundColor Green
Write-Host ""
Write-Host "Now configure the database credentials in your ignored secret file or environment." -ForegroundColor Yellow
Write-Host "  spring.datasource.username = $RootUser" -ForegroundColor Yellow
Write-Host "  spring.datasource.password = (the password supplied during setup)" -ForegroundColor Yellow
