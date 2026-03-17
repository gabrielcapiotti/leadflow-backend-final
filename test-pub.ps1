Write-Host "Testing endpoints..." -ForegroundColor Cyan

# Known valid ID
$id = "3aff1f26-4546-480e-be0c-fd58c79699ab"

# Test 1
Write-Host "`n1. /api/public/settings/{id}" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "SUCCESS: $($r.StatusCode)"
} catch {
    Write-Host "FAIL: $($_.Exception.Response.StatusCode.value__)"
}

# Test 2
Write-Host "`n2. /public/settings/{id}" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "SUCCESS: $($r.StatusCode)"
} catch {
    Write-Host "FAIL: $($_.Exception.Response.StatusCode.value__)"
}
