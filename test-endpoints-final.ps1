Write-Host "=== FINAL ADMIN ENDPOINTS VALIDATION ===" -ForegroundColor Cyan
Write-Host ""

# Login
$headers = @{"Content-Type" = "application/json"}
$body = @{"email" = "admin@example.com"; "password" = "11111111"} | ConvertTo-Json
$loginResponse = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/login" `
  -Method POST `
  -Headers $headers `
  -Body $body `
  -ErrorAction SilentlyContinue

$token = ($loginResponse.Content | ConvertFrom-Json).accessToken

if (-not $token) {
    Write-Host "[FAILED] Could not obtain token" -ForegroundColor Red
    exit
}

Write-Host "[OK] Token acquired" -ForegroundColor Green
Write-Host ""

# Test each endpoint
$endpoints = @(
    @{path = "/admin/overview"; method = "GET"; expected = 200},
    @{path = "/admin/metrics/forecast"; method = "GET"; expected = 200},
    @{path = "/admin/metrics/growth"; method = "GET"; expected = 200},
    @{path = "/admin/metrics/cohorts"; method = "GET"; expected = 200},
    @{path = "/admin/audit/security"; method = "GET"; expected = 200},
    @{path = "/admin/audit/vendor"; method = "GET"; expected = 200}
)

$passed = 0
$failed = 0

foreach ($ep in $endpoints) {
    $url = "http://localhost:8081/api$($ep.path)"
    $headers = @{"Authorization" = "Bearer $token"}
    
    try {
        $response = Invoke-WebRequest -Uri $url `
          -Method $ep.method `
          -Headers $headers `
          -ErrorAction SilentlyContinue
        $statusCode = $response.StatusCode
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value
    }
    
    if ($statusCode -eq $ep.expected) {
        Write-Host "[PASS] $($ep.path) - $statusCode" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] $($ep.path) - Got $statusCode, Expected $($ep.expected)" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host "=== RESULTS ===" -ForegroundColor Cyan
Write-Host "Passed: $passed"
Write-Host "Failed: $failed"
Write-Host "Pass Rate: $(([math]::Round($passed / ($passed + $failed) * 100)))%"

if ($failed -eq 0) {
    Write-Host ""
    Write-Host "ALL ENDPOINTS FIXED [OK]" -ForegroundColor Green
    exit 0
} else {
    exit 1
}
