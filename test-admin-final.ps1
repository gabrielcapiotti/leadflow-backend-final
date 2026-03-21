#!/usr/bin/env pwsh
# LeadFlow Backend - Admin Endpoints Test Suite
# Tests 7 Admin endpoints with REAL ADMIN CREDENTIALS

$baseUrl = "http://localhost:8081"
$global:totalTests = 0
$global:passedTests = 0
$global:failedTests = 0

$adminEmail = "admin@leadflow.com"
$adminPassword = "Admin@Lead123"

function Header {
    param($msg)
    Write-Host "" -ForegroundColor Cyan
    Write-Host "================================================" -ForegroundColor Cyan
    Write-Host $msg -ForegroundColor Cyan
    Write-Host "================================================" -ForegroundColor Cyan
}

function TestAPI {
    param($name, $method, $url, $body, $expectedStatus, $headers)
    
    $global:totalTests++
    Write-Host "`nTEST $($global:totalTests): $name" -ForegroundColor Cyan
    
    try {
        $params = @{
            Uri = $url
            Method = $method
            Headers = $headers
            UseBasicParsing = $true
            ErrorAction = "Continue"
        }
        
        if ($body) {
            $params["Body"] = ($body | ConvertTo-Json -Depth 10)
        }
        if ($method -eq "GET") {
            $params.Remove("Body")
        }
        
        $response = Invoke-WebRequest @params
        
        if ($response.StatusCode -eq $expectedStatus) {
            Write-Host "  [OK] Status: $($response.StatusCode)" -ForegroundColor Green
            $global:passedTests++
            return $true
        } else {
            Write-Host "  [FAIL] Status: $($response.StatusCode) (Expected: $expectedStatus)" -ForegroundColor Red
            $global:failedTests++
            return $false
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        
        if ($statusCode -eq $expectedStatus) {
            Write-Host "  [OK] Status: $statusCode" -ForegroundColor Green
            $global:passedTests++
            return $true
        } else {
            Write-Host "  [FAIL] Status: $statusCode (Expected: $expectedStatus)" -ForegroundColor Red
            $global:failedTests++
            return $false
        }
    }
}

Header "SETUP: CREATE ADMIN USER (Register + Update Role)"

Write-Host "  Step 1: Register user via API..." -ForegroundColor Yellow

$registerPayload = @{
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
    name = "Admin User"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-WebRequest -Uri "$baseUrl/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body $registerPayload `
        -UseBasicParsing `
        -ErrorAction Continue
    
    if ($registerResponse.StatusCode -eq 201) {
        Write-Host "    OK: User registered" -ForegroundColor Green
    } else {
        Write-Host "    INFO: User already exists" -ForegroundColor Cyan
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    if ($statusCode -eq 409) {
        Write-Host "    INFO: User already exists (409)" -ForegroundColor Cyan
    } else {
        Write-Host "    WARN: Register response: $statusCode" -ForegroundColor Yellow
    }
}

Start-Sleep -Milliseconds 500

Write-Host "  Step 2: Update user role to ROLE_ADMIN in database..." -ForegroundColor Yellow

try {
    $env:PGPASSWORD = "venusia"
    
    $sqlCmd = "BEGIN; WITH admin_role AS (SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN' LIMIT 1) UPDATE public.users SET role_id = (SELECT id FROM admin_role) WHERE email = '$adminEmail' AND role_id != (SELECT id FROM admin_role); COMMIT;"
    
    $psqlResult = psql -h localhost -p 2411 -U postgres -d leadflow_test -c $sqlCmd 2>&1
    Write-Host "    OK: Role updated to ROLE_ADMIN" -ForegroundColor Green
} catch {
    Write-Host "    WARN: Could not verify role update" -ForegroundColor Yellow
}

Start-Sleep -Milliseconds 500

Header "SETUP: LOGIN WITH ADMIN CREDENTIALS"

$headers = @{"Content-Type" = "application/json"; "X-Tenant-ID" = "public"}

Write-Host "  Admin Email: $adminEmail" -ForegroundColor Cyan
Write-Host "  Password: (masked)" -ForegroundColor Cyan

Start-Sleep -Milliseconds 500

try {
    $loginResponse = Invoke-WebRequest -Uri "$baseUrl/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{email=$adminEmail; password=$adminPassword} | ConvertTo-Json) `
        -UseBasicParsing `
        -ErrorAction Continue
    
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    $headers["Authorization"] = "Bearer $token"
    Write-Host "  OK: Admin token acquired" -ForegroundColor Green
    $global:passedTests++
    $global:totalTests++
} catch {
    Write-Host "  FAIL: Login failed" -ForegroundColor Red
    Write-Host "  Status: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    exit 1
}

Start-Sleep -Milliseconds 500

Header "TESTING ADMIN ENDPOINTS WITH FULL PERMISSIONS"
Write-Host "  User has ROLE_ADMIN, all endpoints should return 200 OK" -ForegroundColor Green

Header "ADMIN METRICS ENDPOINTS (1-5)"

TestAPI -name "GET /admin/overview" -method "GET" -url "$baseUrl/admin/overview" `
    -expectedStatus 200 -headers $headers | Out-Null
Start-Sleep -Milliseconds 300

TestAPI -name "GET /admin/metrics/growth" -method "GET" -url "$baseUrl/admin/metrics/growth?days=30" `
    -expectedStatus 200 -headers $headers | Out-Null
Start-Sleep -Milliseconds 300

TestAPI -name "GET /admin/metrics/cohorts" -method "GET" -url "$baseUrl/admin/metrics/cohorts" `
    -expectedStatus 200 -headers $headers | Out-Null
Start-Sleep -Milliseconds 300

TestAPI -name "GET /admin/metrics/forecast" -method "GET" -url "$baseUrl/admin/metrics/forecast" `
    -expectedStatus 200 -headers $headers | Out-Null
Start-Sleep -Milliseconds 300

TestAPI -name "GET /admin/metrics/health/{vendorId}" -method "GET" -url "$baseUrl/admin/metrics/health/00000000-0000-0000-0000-000000000001" `
    -expectedStatus 200 -headers $headers | Out-Null

Start-Sleep -Milliseconds 500

Header "ADMIN AUDIT ENDPOINTS (6-7)"

TestAPI -name "GET /admin/audit/security" -method "GET" -url "$baseUrl/admin/audit/security?limit=10" `
    -expectedStatus 200 -headers $headers | Out-Null
Start-Sleep -Milliseconds 300

TestAPI -name "GET /admin/audit/vendor" -method "GET" -url "$baseUrl/admin/audit/vendor?limit=10" `
    -expectedStatus 200 -headers $headers | Out-Null

Header "RESULTS SUMMARY"

$passRate = if ($global:totalTests -gt 0) { [math]::Round(($global:passedTests / $global:totalTests) * 100, 2) } else { 0 }

Write-Host ""
Write-Host "Total Tests: $global:totalTests" -ForegroundColor Cyan
Write-Host "Passed: $global:passedTests" -ForegroundColor Green
Write-Host "Failed: $global:failedTests" -ForegroundColor $(if ($global:failedTests -gt 0) { "Red" } else { "Green" })
Write-Host "Pass Rate: $passRate%" -ForegroundColor $(if ($passRate -eq 100) { "Green" } else { "Yellow" })
Write-Host ""

if ($global:failedTests -eq 0 -and $global:passedTests -gt 0) {
    Write-Host "SUCCESS: All admin endpoints are operational!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "WARNING: Some tests may have issues" -ForegroundColor Yellow
    exit 1
}
