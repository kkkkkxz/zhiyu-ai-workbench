# Generate a local demo .env without committing credentials to the repository.
[CmdletBinding()]
param(
    [string]$Path = (Join-Path $PSScriptRoot '..\..\deploy\.env')
)

$ErrorActionPreference = 'Stop'

if (Test-Path -LiteralPath $Path) {
    Write-Host "[SKIP] $Path already exists"
    exit 0
}

function New-RandomSecret {
    param([int]$Bytes = 32)
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return ([System.BitConverter]::ToString($buffer) -replace '-', '').ToLowerInvariant()
}

$parent = Split-Path -Parent $Path
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}

$content = @"
# Generated locally. Do not commit this file.
DASHSCOPE_API_KEY=
MYSQL_ROOT_PASSWORD=$(New-RandomSecret)
MYSQL_PASSWORD=$(New-RandomSecret)
MYSQL_DB=zwskchat
MYSQL_USER=zwsk
REDIS_PASSWORD=
RABBIT_USER=zwsk
RABBIT_PASSWORD=$(New-RandomSecret)
MINIO_ACCESS_KEY=zwskminio
MINIO_SECRET_KEY=$(New-RandomSecret)
MINIO_BUCKET=zwsk-kb
JWT_SECRET=$(New-RandomSecret 48)
QDRANT_API_KEY=
ZWSK_BOOTSTRAP_RESET_ADMIN_PASSWORD=true
ZWSK_BOOTSTRAP_ADMIN_DEFAULT_PASSWORD=$(New-RandomSecret 24)
"@

Set-Content -LiteralPath $Path -Value $content -Encoding UTF8 -NoNewline
Write-Host "[OK] Generated local credentials in $Path"
Write-Host "[INFO] The initial admin password is stored only in that local ignored file."
