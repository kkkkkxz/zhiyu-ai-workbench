# ============================================================
# 01-prepare-dirs.ps1
# Create D:\conf\zwsk dir tree and copy secret yaml template.
# Non-destructive, idempotent.
# ============================================================
$ErrorActionPreference = 'Stop'

$Root = 'D:\conf\zwsk'
$Subs = @(
    'D:\conf\zwsk',
    'D:\conf\zwsk\logs',
    'D:\conf\zwsk\scripts',
    'D:\conf\mysql',
    'D:\conf\mysql\data',
    'D:\conf\mysql\log',
    'D:\conf\qdrant',
    'D:\conf\qdrant\storage',
    'D:\conf\qdrant\snapshots'
)

foreach ($d in $Subs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Force -Path $d | Out-Null
        Write-Host "[CREATE] $d" -ForegroundColor Green
    } else {
        Write-Host "[OK]     $d" -ForegroundColor DarkGray
    }
}

# Copy secret template only if target does not exist
$Template = Join-Path $PSScriptRoot 'zwsk-secret.example.yaml'
$Target   = Join-Path $Root 'zwsk-secret.yaml'
if (-not (Test-Path $Target)) {
    if (-not (Test-Path $Template)) {
        Write-Warning "Template not found: $Template"
    } else {
        Copy-Item $Template $Target
        Write-Host "[COPY]   $Template -> $Target" -ForegroundColor Green
        Write-Host "Edit $Target and fill in your DashScope API key and DB password." -ForegroundColor Yellow
    }
} else {
    Write-Host "[OK]     $Target (exists, not overwritten)" -ForegroundColor DarkGray
}

# Tighten ACL: current user read; admins full
try {
    icacls $Target /inheritance:r /grant:r "$($env:USERNAME):R" /grant:r "Administrators:F" | Out-Null
    Write-Host "[ACL]    $Target locked" -ForegroundColor Green
} catch {
    Write-Warning "icacls failed: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1) Open $Target and fill real API key + DB password" -ForegroundColor Cyan
Write-Host "  2) Run 04-install-qdrant.ps1 (Qdrant Windows binary + scheduled task)" -ForegroundColor Cyan
Write-Host "  3) Run 05-init-mysql-db.ps1 (create DB + import schema)" -ForegroundColor Cyan
