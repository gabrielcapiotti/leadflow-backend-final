$headers = @{
    "Authorization" = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjYXJsb3NAZWx0YWRlLmNvbSIsInRlbmFudElkIjoicHVibGljIiwiaWQiOiI0ZGFhNzFlYi02ZWVlLTRhNGMtOGY2ZC1jZTVkYTEwN2U5ZjMiLCJyb2xlcyI6WyJVU0VSIl0sImlhdCI6MTcxMDQwMDA4MCwiZXhwIjo5OTk5OTk5OTk5fQ.P5GPmwhJk3gWDz95zH-0R3mV3jXH0uYk5UazL0kZCJ0"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$baseUrl = "http://localhost:8081"
$pass = 0
$fail = 0

Write-Host "=== TESTANDO ENDPOINTS REFATORADOS ===" -ForegroundColor Cyan

# Test 1: PUT /api/me/settings
Write-Host "[1] PUT /api/me/settings" -ForegroundColor Yellow
$body = @{
    vendorName = "Test Vendor"
    companyName = "Test Company"
} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PUT -Headers $headers -Body $body 2>&1
if ($r.StatusCode -eq 200) { Write-Host "  OK (200)" -ForegroundColor Green; $pass++ } else { Write-Host "  FAIL ($($r.StatusCode))" -ForegroundColor Red; $fail++ }

# Test 2: GET /api/me/settings
Write-Host "[2] GET /api/me/settings" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method GET -Headers $headers 2>&1
if ($r.StatusCode -eq 200) { Write-Host "  OK (200)" -ForegroundColor Green; $pass++ } else { Write-Host "  FAIL ($($r.StatusCode))" -ForegroundColor Red; $fail++ }

# Test 3: PATCH /api/me/settings
Write-Host "[3] PATCH /api/me/settings" -ForegroundColor Yellow
$body = @{ vendorName = "Updated" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PATCH -Headers $headers -Body $body 2>&1
if ($r.StatusCode -eq 200) { Write-Host "  OK (200)" -ForegroundColor Green; $pass++ } else { Write-Host "  FAIL ($($r.StatusCode))" -ForegroundColor Red; $fail++ }

# Test 4: DELETE /api/me/settings
Write-Host "[4] DELETE /api/me/settings" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method DELETE -Headers $headers 2>&1
if ($r.StatusCode -eq 204) { Write-Host "  OK (204)" -ForegroundColor Green; $pass++ } else { Write-Host "  FAIL ($($r.StatusCode))" -ForegroundColor Red; $fail++ }

Write-Host "`nResultado: $pass passou, $fail falhou" -ForegroundColor Cyan
