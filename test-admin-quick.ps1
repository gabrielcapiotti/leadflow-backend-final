Write-Host "=== ADMIN ENDPOINTS - FINAL VALIDATION ===" -ForegroundColor Cyan
Write-Host ""

# Get existing token from database
$query = "SELECT token FROM user_sessions WHERE user_id IN (SELECT id FROM users WHERE role = 'ADMIN') LIMIT 1;"
$token = psql -h localhost -p 2411 -U leadflow_user -d leadflow_test -t -c $query 2>/dev/null | ForEach-Object {$_.Trim()} | Where-Object {$_ -ne ""} | Select-Object -First 1

if (-not $token) {
    Write-Host "[ERROR] Could not retrieve admin token from database" -ForegroundColor Red
    Write-Host "Trying to create session manually..."
    # Create a test token as fallback
    $token = "test-token"
}

Write-Host "[OK] Using token: $($token.Substring(0, 20))..." -ForegroundColor Green
Write-Host ""

# Test endpoints without auth requirement (they should still work)
$endpoints = @(
    @{path = "/admin/overview"; expected = 200},
    @{path = "/admin/metrics/forecast"; expected = 200},
    @{path = "/admin/metrics/growth"; expected = 200},
    @{path = "/admin/metrics/cohorts"; expected = 200},
    @{path = "/admin/audit/security"; expected = 200},
    @{path = "/admin/audit/vendor"; expected = 200}
)

$passed = 0
$failed = 0

foreach ($ep in $endpoints) {
    $url = "http://localhost:8081/api$($ep.path)"
    $headers = @{"Authorization" = "Bearer $token"}
    
    try {
        $response = Invoke-WebRequest -Uri $url `
          -Method GET `
          -Headers $headers `
          -SkipHttpErrorCheck `
          -TimeoutSec 5
        $statusCode = $response.StatusCode
    } catch {
        $statusCode = 500
    }
    
    if ($statusCode -eq $ep.expected) {
        Write-Host "[PASS] $($ep.path) - Status $statusCode" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "[FAIL] $($ep.path) - Status $statusCode (Expected $($ep.expected))" -ForegroundColor Red
        $failed++
    }
}

Write-Host ""
Write-Host "=== SUMMARY ===" -ForegroundColor Cyan
Write-Host "Total: $($passed + $failed)"
Write-Host "Passed: $passed"
Write-Host "Failed: $failed"
if ($failed -eq 0) {
    Write-Host "PASS RATE: 100% [ALL ENDPOINTS FIXED]" -ForegroundColor Green
} else {
    Write-Host "PASS RATE: $(([math]::Round($passed / ($passed + $failed) * 100)))%" -ForegroundColor Yellow
}
