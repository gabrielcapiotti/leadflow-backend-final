$token = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJiZmE3MzJlNC04ZjRmLTQ1ZGQtOTE2OC0zMjUwMjhlYzEzYTQiLCJzdWIiOiJjYXJsb3NAbGVhZGZsb3cuY29tIiwiaXNzIjoibGVhZGZsb3ciLCJpYXQiOjE3NzM3NjgxNzUsImV4cCI6MTc3Mzc3MTc3NSwidXNlcklkIjoiY2YyNWE1ZGMtMWRiNC00NzlhLTlkODUtZGNmOThmNzc5NjA5Iiwicm9sZSI6IlJPTEVfVVNFUiIsInRlbmFudCI6InB1YmxpYyJ9.TOXcNIAL_WwFMVOgVYbmRzmygexz4Pz548KJqVFRums"

$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$pass = 0
$fail = 0

Write-Host "=== TEST REFACTORED ENDPOINTS ===" -ForegroundColor Green

$tests = @(
    @{ method = "PUT"; path = "/api/me/settings"; desc = "Create user settings" }
    @{ method = "GET"; path = "/api/me/settings"; desc = "Get user settings" }
    @{ method = "PATCH"; path = "/api/me/settings"; desc = "Partial update" }
    @{ method = "DELETE"; path = "/api/me/settings"; desc = "Delete user settings" }
)

foreach ($test in $tests) {
    Write-Host "$($test.method) $($test.path)" -ForegroundColor Yellow -NoNewline
    
    $body = $null
    if ($test.method -eq "PUT") {
        $body = @{ vendorName = "Test"; companyName = "Test Co" } | ConvertTo-Json
    }
    if ($test.method -eq "PATCH") {
        $body = @{ vendorName = "Updated" } | ConvertTo-Json
    }
    
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8081$($test.path)" -Method $test.method -Headers $headers -Body $body -UseBasicParsing
        Write-Host " => OK ($($r.StatusCode))" -ForegroundColor Green
        $pass++
    }
    catch {
        Write-Host " => FAIL" -ForegroundColor Red
        $fail++
    }
}

Write-Host "`nResult: $pass OK | $fail FAIL"
