Write-Host "Testing direct path..." -ForegroundColor Cyan
$id = "3aff1f26-4546-480e-be0c-fd58c79699ab"

# Test 1: /api/public/settings/{id}
Write-Host "`n1. Testing /api/public/settings/$id"
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "Success: $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Fail: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# Test 2: /public/settings/{id}
Write-Host "`n2. Testing /public/settings/$id"
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "Success: $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Fail: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# Test 3: /auth/login (known public endpoint for comparison)
Write-Host "`n3. Testing /auth/login (known public)"
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body '{}' -UseBasicParsing
    Write-Host "Success: $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Expected fail: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
}
