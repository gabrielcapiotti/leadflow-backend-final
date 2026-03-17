$token = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJiZmE3MzJlNC04ZjRmLTQ1ZGQtOTE2OC0zMjUwMjhlYzEzYTQiLCJzdWIiOiJjYXJsb3NAbGVhZGZsb3cuY29tIiwiaXNzIjoibGVhZGZsb3ciLCJpYXQiOjE3NzM3NjgxNzUsImV4cCI6MTc3Mzc3MTc3NSwidXNlcklkIjoiY2YyNWE1ZGMtMWRiNC00NzlhLTlkODUtZGNmOThmNzc5NjA5Iiwicm9sZSI6IlJPTEVfVVNFUiIsInRlbmFudCI6InB1YmxpYyJ9.TOXcNIAL_WwFMVOgVYbmRzmygexz4Pz548KJqVFRums"

$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$baseUrl = "http://localhost:8081"
$pass = 0
$fail = 0

Write-Host "=== TESTANDO ENDPOINTS REFATORADOS ===" -ForegroundColor Cyan
Write-Host "Paths: /api/me/settings (user-bound) + /api/settings/{id} (admin)" -ForegroundColor Yellow

try {
    # Test 1: PUT /api/me/settings
    Write-Host "[1] PUT /api/me/settings" -NoNewline -ForegroundColor Yellow
    $body = @{ vendorName = "Test Vendor"; companyName = "Test Company" } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PUT -Headers $headers -Body $body -UseBasicParsing
    $id = ($r.Content | ConvertFrom-Json).id
    Write-Host " => 200 OK" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 2: GET /api/me/settings
    Write-Host "[2] GET /api/me/settings" -NoNewline -ForegroundColor Yellow
    $r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method GET -Headers $headers -UseBasicParsing
    Write-Host " => 200 OK" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 3: PATCH /api/me/settings
    Write-Host "[3] PATCH /api/me/settings" -NoNewline -ForegroundColor Yellow
    $body = @{ vendorName = "Updated Vendor" } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PATCH -Headers $headers -Body $body -UseBasicParsing
    Write-Host " => 200 OK" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 4: GET /api/settings/{id} (Admin)
    Write-Host "[4] GET /api/settings/{id}" -NoNewline -ForegroundColor Yellow
    $r = Invoke-WebRequest -Uri "$baseUrl/api/settings/$id" -Method GET -Headers $headers -UseBasicParsing
    Write-Host " => 200 OK" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 5: PUT /api/settings/{id} (Admin)
    Write-Host "[5] PUT /api/settings/{id}" -NoNewline -ForegroundColor Yellow
    $body = @{ vendorName = "Admin Updated"; companyName = "Admin Company" } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$baseUrl/api/settings/$id" -Method PUT -Headers $headers -Body $body -UseBasicParsing
    Write-Host " => 200 OK" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 6: DELETE /api/me/settings
    Write-Host "[6] DELETE /api/me/settings" -NoNewline -ForegroundColor Yellow
    $r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method DELETE -Headers $headers -UseBasicParsing
    Write-Host " => 204 No Content" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

try {
    # Test 7: DELETE /api/settings/{id} (Admin)
    Write-Host "[7] DELETE /api/settings/{id}" -NoNewline -ForegroundColor Yellow
    $r = Invoke-WebRequest -Uri "$baseUrl/api/settings/$id" -Method DELETE -Headers $headers -UseBasicParsing
    Write-Host " => 204 No Content" -ForegroundColor Green; $pass++
} catch {
    Write-Host " => FAIL" -ForegroundColor Red; $fail++
}

} catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`nResultado: $pass PASSOU | $fail FALHOU" -ForegroundColor Magenta
