Write-Host "TEST: Debug registration response" -ForegroundColor Cyan

# 1. Register
$body = @{
    email = "test$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json

Write-Host "Request: $body" -ForegroundColor Gray

$rawResp = Invoke-WebRequest http://localhost:8081/auth/register `
    -Method POST `
    -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -Body $body `
    -UseBasicParsing

Write-Host "Response status: $($rawResp.StatusCode)" -ForegroundColor Green
Write-Host "Response content:`n$($rawResp.Content)" -ForegroundColor Gray

$resp = $rawResp.Content | ConvertFrom-Json

Write-Host "Parsed response:" -ForegroundColor Cyan
Write-Host "  userId: '$($resp.userId)'" -ForegroundColor Green
Write-Host "  accessToken length: $($resp.accessToken.Length)" -ForegroundColor Green
Write-Host "  Properties: $(($resp | Get-Member -MemberType NoteProperty).Name -join ', ')" -ForegroundColor Gray
