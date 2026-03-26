#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"
$testEmail = "test-debug-$(Get-Random)@leadflow.dev"
$testPassword = "Test123456!"

$Headers = @{
    "Content-Type" = "application/json"
}

# REGISTER
Write-Host "REGISTER: $testEmail" -ForegroundColor Cyan
$body = @{
    name = "Debug"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "$BaseUrl/auth/register" -Method POST -Headers $Headers -Body $body 2>&1
$statusCode = $response.StatusCode
$content = $response.Content | ConvertFrom-Json

Write-Host "Register Status: $statusCode" -ForegroundColor Green
Write-Host "Register Response:" -ForegroundColor Yellow
$content | ConvertTo-Json | Write-Host

$capturedTenantId = $content.tenantId
Write-Host "`nCaptured tenantId: '$capturedTenantId'" -ForegroundColor Magenta
Write-Host "Starts with 't_': $($capturedTenantId.StartsWith('t_'))" -ForegroundColor Magenta
Write-Host "Is UUID format: $($capturedTenantId -match '^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$')" -ForegroundColor Magenta

# LOGIN with captured tenantId
Start-Sleep -Milliseconds 500
Write-Host "`nLOGIN with tenant: $capturedTenantId" -ForegroundColor Cyan

$loginHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $capturedTenantId
}

$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

$loginResponse = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method POST -Headers $loginHeaders -Body $loginBody -ErrorVariable loginErr 2>&1

if ($loginErr) {
    Write-Host "LOGIN FAILED!" -ForegroundColor Red
    Write-Host "Error: $loginErr" -ForegroundColor Red
    if ($loginResponse) {
        Write-Host "Status: $($loginResponse.StatusCode)"
        Write-Host "Content: $($loginResponse.Content)"
    }
} else {
    Write-Host "Login Status: $($loginResponse.StatusCode)" -ForegroundColor Green
    $loginContent = $loginResponse.Content | ConvertFrom-Json
    Write-Host "Login Response:" -ForegroundColor Yellow
    $loginContent | ConvertTo-Json | Write-Host
}

# Show server logs
Write-Host "`nLast 5 register/login attempts from server:" -ForegroundColor Cyan
Get-Content server.log | Select-String "User registered|Login attempt|GENERATING.*JWT" | Select-Object -Last 5 | ForEach-Object {
    Write-Host "  $_" -ForegroundColor Gray
}
