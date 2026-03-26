#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Debug script to isolate tenant ID mismatch issue
#>

$BaseUrl = "http://localhost:8081"
$testEmail = "debug-$(Get-Random)@leadflow.dev"
$testPassword = "Test123456!"

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body = $null,
        [string]$TenantId = $null
    )
    
    $Headers = @{
        "Content-Type" = "application/json"
        "User-Agent"   = "Debug-Test"
    }
    
    if ($TenantId) {
        $Headers["X-Tenant-ID"] = $TenantId
        Write-Host "  → Sending header X-Tenant-ID: $TenantId" -ForegroundColor Cyan
    }
    
    try {
        $params = @{
            Uri     = "$BaseUrl$Endpoint"
            Method  = $Method
            Headers = $Headers
        }
        
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json)
        }
        
        $response = Invoke-RestMethod @params
        return @{ Success = $true; Data = $response; Status = 200 }
    }
    catch {
        $status = $_.Exception.Response.StatusCode.Value__
        $errorBody = try { $_.ErrorDetails.Message | ConvertFrom-Json } catch { $_ }
        return @{ Success = $false; Data = $errorBody; Status = $status }
    }
}

Write-Host "`n=== TENANT ID DEBUG TEST ===" -ForegroundColor Green

# Step 1: Register
Write-Host "`n[STEP 1] REGISTER USER" -ForegroundColor Yellow
Write-Host "Email: $testEmail"
Write-Host "Password: $testPassword"

$registerResp = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Debug User"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if ($registerResp.Success) {
    $fullResp = $registerResp.Data
    Write-Host "`n✓ Register Success (HTTP 200)" -ForegroundColor Green
    
    Write-Host "`nFull Response:" -ForegroundColor Cyan
    $fullResp | ConvertTo-Json | Write-Host
    
    $tenantIdFromRegister = $fullResp.tenantId
    Write-Host "`n📊 Extracted tenantId from response: '$tenantIdFromRegister'" -ForegroundColor Magenta
    Write-Host "   Type: $($tenantIdFromRegister.GetType().Name)" -ForegroundColor Magenta
    Write-Host "   Length: $($tenantIdFromRegister.Length)" -ForegroundColor Magenta
    Write-Host "   Starts with 't_': $($tenantIdFromRegister.StartsWith('t_'))" -ForegroundColor Magenta
    
    $accessToken = $fullResp.accessToken
    Write-Host "`n📊 Access Token prefix: $($accessToken.Substring(0, 30))..." -ForegroundColor Magenta
}
else {
    Write-Host "✗ Register Failed (HTTP $($registerResp.Status))" -ForegroundColor Red
    Write-Host "Error: $($registerResp.Data)" -ForegroundColor Red
    exit 1
}

# Step 2: Immediate Login with Captured Tenant ID
Write-Host "`n[STEP 2] LOGIN WITH CAPTURED TENANT ID" -ForegroundColor Yellow
Write-Host "Using tenantId from register: $tenantIdFromRegister"

$loginResp = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
} -TenantId $tenantIdFromRegister

if ($loginResp.Success) {
    Write-Host "✓ Login Success (HTTP $($loginResp.Status))" -ForegroundColor Green
    $loginResp.Data | ConvertTo-Json | Write-Host
} else {
    Write-Host "✗ Login Failed (HTTP $($loginResp.Status))" -ForegroundColor Red
    Write-Host "Response: $($loginResp.Data | ConvertTo-Json)" -ForegroundColor Red
}

# Step 3: Check server logs for tenant mismatch
Write-Host "`n[STEP 3] CHECKING SERVER LOGS" -ForegroundColor Yellow
$logFile = "server.log"
if (Test-Path $logFile) {
    Write-Host "Recent log entries:" -ForegroundColor Cyan
    Get-Content $logFile | Select-String "Login attempt|tenant=|Generated new tenant" | Select-Object -Last 15 | ForEach-Object {
        Write-Host "  $_" -ForegroundColor Gray
    }
}

Write-Host "`n=== END DEBUG TEST ===" -ForegroundColor Green
