# ============================================================
# start-backend.ps1
# Convenience entrypoint for local backend development.
# It starts middleware first, then launches Spring Boot backend.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\start-backend.ps1
#   powershell -ExecutionPolicy Bypass -File .\start-backend.ps1 -StrictMiddleware
#   powershell -ExecutionPolicy Bypass -File .\start-backend.ps1 -SkipMiddleware
# ============================================================
[CmdletBinding()]
param(
    [switch]$SkipMiddleware,
    [switch]$StrictMiddleware
)

$ErrorActionPreference = 'Stop'
$script = Join-Path $PSScriptRoot 'scripts\win\start-zwsk.ps1'
if (-not (Test-Path $script)) {
    Write-Error "Backend startup script not found: $script"
    exit 1
}

$argsForStart = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script)
if ($SkipMiddleware) { $argsForStart += '-SkipMiddleware' }
if ($StrictMiddleware) { $argsForStart += '-StrictMiddleware' }

& powershell.exe @argsForStart
exit $LASTEXITCODE
