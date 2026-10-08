param(
  [string]$EnvFile = ".env",
  [string]$BackupDir = "backups\mysql",
  [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path $EnvFile)) {
  throw "$EnvFile not found"
}

$envMap = @{}
Get-Content $EnvFile | ForEach-Object {
  if ($_ -match '^\s*([^#=]+)=(.*)$') { $envMap[$matches[1].Trim()] = $matches[2].Trim() }
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$db = if ($envMap["MYSQL_DB"]) { $envMap["MYSQL_DB"] } else { "zwskchat" }
$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $BackupDir "$db-$ts.sql"

cmd /c "docker exec zwsk-mysql sh -c 'exec mysqldump --single-transaction --routines --triggers -uroot -p`"`$MYSQL_ROOT_PASSWORD`" `"`$MYSQL_DATABASE`"' > `"$out`""
if ($LASTEXITCODE -ne 0) {
  throw "mysqldump failed with exit code $LASTEXITCODE"
}

Get-ChildItem -Path $BackupDir -Filter "*.sql" |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
  Remove-Item -Force

Write-Host "Backup written: $out"
