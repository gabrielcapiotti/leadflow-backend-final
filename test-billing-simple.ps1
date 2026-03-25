#!/usr/bin/env pwsh

$ErrorActionPreference = "SilentlyContinue"
$ProgressPreference = "SilentlyContinue"

function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    $payload = $payload.Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    try {
        return [System.Text.Encoding]::UTF8.GetString(
            [System.Convert]::FromBase64String($payload)
        ) | ConvertFrom-Json
    } catch {
        return $null
    }
}

$baseUrl = "http://localhost:8081"
$tenantId = "public"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BILLING SUBSCRIPTION ENDPOINTS TEST"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

function Get-Headers($token=$null) {
    $headers = @{
        "X-Tenant-Id" = $tenantId
        "Content-Type" = "application/json"
    }
    if ($token) {
        $headers["Authorization"] = "Bearer $token"
    }
    return $headers
}

# Register user
$email = "billing-test-$(Get-Random)@test.com"
$reg = @{
    email = $email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Billing Test"
} | ConvertTo-Json

Write-Host "Registering user: $email" -ForegroundColor Yellow
$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers (Get-Headers) `
    -Body $reg `
    -UseBasicParsing

$data = $res.Content | ConvertFrom-Json
$token = $data.accessToken
$decoded = Decode-Jwt $token
$userId = $decoded.userId

Write-Host "✅ User registered: $userId" -ForegroundColor Green
Write-Host ""

$passed = 0
$failed = 0

# TEST 1: GET /api/v1/billing/subscription
Write-Host "[TEST 1] GET /api/v1/billing/subscription" -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/api/v1/billing/subscription" `
        -Method GET `
        -Headers (Get-Headers $token) `
        -UseBasicParsing
    $data = $res.Content | ConvertFrom-Json
    Write-Host "✅ Status 200 - Subscription retrieved" -ForegroundColor Green
    Write-Host "   Plan: $($data.planName), Status: $($data.status)" -ForegroundColor Green
    $passed++
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status $status" -ForegroundColor Red
    $failed++
}
Write-Host ""

# TEST 2: GET /api/v1/billing/usage
Write-Host "[TEST 2] GET /api/v1/billing/usage" -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/api/v1/billing/usage" `
        -Method GET `
        -Headers (Get-Headers $token) `
        -UseBasicParsing
    $data = $res.Content | ConvertFrom-Json
    Write-Host "✅ Status 200 - Usage data retrieved" -ForegroundColor Green
    $passed++
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status $status" -ForegroundColor Red
    $failed++
}
Write-Host ""

# TEST 3: POST /api/v1/billing/subscription (NEW ENDPOINT)
Write-Host "[TEST 3] POST /api/v1/billing/subscription (CREATE)" -ForegroundColor Yellow
try {
    $body = @{ planId = "FREE" } | ConvertTo-Json
    $res = Invoke-WebRequest "$baseUrl/api/v1/billing/subscription" `
        -Method POST `
        -Headers (Get-Headers $token) `
        -Body $body `
        -UseBasicParsing
    $data = $res.Content | ConvertFrom-Json
    Write-Host "✅ Status 200 - Subscription created" -ForegroundColor Green
    Write-Host "   Response: $($data | ConvertTo-Json -Compress)" -ForegroundColor Green
    $passed++
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $content = $_.Exception.Response.Content.ReadAsStream() | % { [System.IO.StreamReader]::new($_).ReadToEnd() }
    Write-Host "❌ Status $status" -ForegroundColor Red
    if ($content) {
        Write-Host "   Error: $content" -ForegroundColor Red
    }
    $failed++
}
Write-Host ""

# TEST 4: PUT /api/v1/billing/subscription (NEW ENDPOINT)
Write-Host "[TEST 4] PUT /api/v1/billing/subscription (UPDATE)" -ForegroundColor Yellow
try {
    $body = @{ planId = "FREE" } | ConvertTo-Json
    $res = Invoke-WebRequest "$baseUrl/api/v1/billing/subscription" `
        -Method PUT `
        -Headers (Get-Headers $token) `
        -Body $body `
        -UseBasicParsing
    $data = $res.Content | ConvertFrom-Json
    Write-Host "✅ Status 200 - Subscription updated" -ForegroundColor Green
    Write-Host "   Plan: $($data.planName), Status: $($data.status)" -ForegroundColor Green
    $passed++
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $content = $_.Exception.Response.Content.ReadAsStream() | % { [System.IO.StreamReader]::new($_).ReadToEnd() }
    Write-Host "❌ Status $status" -ForegroundColor Red
    if ($content) {
        Write-Host "   Error: $content" -ForegroundColor Red
    }
    $failed++
}
Write-Host ""

# SUMMARY
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red
Write-Host ""

if ($failed -eq 0) {
    Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "❌ SOME TESTS FAILED" -ForegroundColor Red
}

Write-Host ""
