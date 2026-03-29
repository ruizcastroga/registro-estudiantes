# =============================================================================
# seed_api.ps1 — Load all mock data into the running API (Windows PowerShell)
#
# Usage:
#   .\scripts\seed_api.ps1 -ApiKey "your-api-key-here"
#
# Example:
#   .\scripts\seed_api.ps1 -ApiKey "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$ApiKey
)

$BaseUrl  = "http://localhost:8080"
$DataDir  = Join-Path $PSScriptRoot "mock_data"
$Headers  = @{ "X-API-Key" = $ApiKey; "Content-Type" = "application/json" }

function Check-Server {
    Write-Host "`n==> Checking server at $BaseUrl..." -ForegroundColor Cyan
    try {
        $r = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Headers $Headers -Method Get
        Write-Host "    OK: status=$($r.status)  db=$($r.db)" -ForegroundColor Green
    } catch {
        Write-Host "    ERROR: Cannot reach $BaseUrl/api/health" -ForegroundColor Red
        Write-Host "    Make sure the app is running and the API key is correct."
        exit 1
    }
}

function Import-Resource {
    param([string]$Label, [string]$Endpoint, [string]$File)
    Write-Host "`n==> Importing $Label..." -ForegroundColor Cyan
    $body = Get-Content $File -Raw
    try {
        $r = Invoke-RestMethod -Uri "$BaseUrl$Endpoint" -Headers $Headers -Method Post -Body $body
        Write-Host "    OK: imported=$($r.imported)  skipped=$($r.skipped)" -ForegroundColor Green
        if ($r.errors.Count -gt 0) {
            $r.errors | Select-Object -First 5 | ForEach-Object { Write-Host "    ! $_" -ForegroundColor Yellow }
        }
    } catch {
        Write-Host "    ERROR: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# --------------------------------------------------------------------------
Check-Server
Import-Resource -Label "Students"       -Endpoint "/api/students/import"        -File "$DataDir\students.json"
Import-Resource -Label "Staff"          -Endpoint "/api/staff/import"           -File "$DataDir\staff.json"
Import-Resource -Label "Visitor Badges" -Endpoint "/api/visitors/badges/import" -File "$DataDir\visitor_badges.json"

Write-Host "`n==> Seed complete. Verify with:" -ForegroundColor Green
Write-Host "    Invoke-RestMethod -Uri '$BaseUrl/api/students/export' -Headers @{'X-API-Key'='$ApiKey'} | ConvertTo-Json"
Write-Host ""
