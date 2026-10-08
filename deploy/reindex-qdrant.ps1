param(
  [string]$BaseUrl = "http://127.0.0.1/api",
  [string]$Username = "admin",
  [string]$Password,
  [long]$TenantId = 0,
  [long]$KbId = 0,
  [int]$Limit = 0,
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not $Password) {
  $secure = Read-Host "Password for $Username" -AsSecureString
  $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  )
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" -Body $loginBody
if ($login.code -ne 0) {
  throw "Login failed: $($login.message)"
}

$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token" }

$qs = @()
if ($TenantId -gt 0) { $qs += "tenantId=$TenantId" }
if ($KbId -gt 0) { $qs += "kbId=$KbId" }
if ($Limit -gt 0) { $qs += "limit=$Limit" }
if ($DryRun) { $qs += "dryRun=true" }
$query = if ($qs.Count -gt 0) { "?" + ($qs -join "&") } else { "" }

$result = Invoke-RestMethod -Method Post -Uri "$BaseUrl/ops/rag/reindex-existing-chunks$query" -Headers $headers
$result | ConvertTo-Json -Depth 8
