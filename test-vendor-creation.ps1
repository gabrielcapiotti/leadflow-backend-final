#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TESTE: Vendor Creation After Registration" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan

# Step 1: Register new user
Write-Host "`n[1/4] Registering new user..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "vendor-test-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

$headers = @{"Content-Type" = "application/json"}

try {
    $regResp = Invoke-RestMethod -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers $headers `
        -Body (@{
            name = "Vendor Test User"
            email = $testEmail
            password = $testPassword
            confirmPassword = $testPassword
        } | ConvertTo-Json)
    
    $token = $regResp.accessToken
    Write-Host "✅ User registered: $testEmail" -ForegroundColor Green
} catch {
    Write-Host "❌ Registration failed: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Decode JWT to get tenant
Write-Host "`n[2/4] Extracting tenant from JWT..." -ForegroundColor Yellow

function Decode-JWT {
    param([string]$Token)
    
    $parts = $Token.Split('.')
    if ($parts.Length -ne 3) { return $null }
    
    $payload = $parts[1]
    $padding = 4 - ($payload.Length % 4)
    if ($padding -ne 4) { $payload += "=" * $padding }
    
    try {
        $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
        return $decoded | ConvertFrom-Json
    } catch {
        return $null
    }
}

$jwtClaims = Decode-JWT $token
if ($jwtClaims) {
    $tenant = $jwtClaims.tenant
    Write-Host "✅ Tenant extracted: $tenant" -ForegroundColor Green
} else {
    Write-Host "❌ Failed to decode JWT" -ForegroundColor Red
    exit 1
}

# Step 3: Test if vendor was auto-created
Write-Host "`n[3/4] Testing vendor access (should work if auto-created)..." -ForegroundColor Yellow

$authHeaders = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = $tenant
    "Content-Type" = "application/json"
}

try {
    $vendorResp = Invoke-RestMethod -Uri "$baseUrl/vendors" `
        -Method GET `
        -Headers $authHeaders
    
    Write-Host "✅ GET /vendors successful" -ForegroundColor Green
    Write-Host "   Vendors found: $($vendorResp.content.Count)" -ForegroundColor Gray
    
    if ($vendorResp.content.Count -gt 0) {
        Write-Host "   ✅ VENDOR AUTO-CREATED: $($vendorResp.content[0].id)" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  No vendors found (auto-creation may be missing)" -ForegroundColor Yellow
    }
} catch {
    $status = 0
    try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
    Write-Host "❌ GET /vendors failed: HTTP $status" -ForegroundColor Red
}

# Step 4: Test metrics endpoint (requires vendor)
Write-Host "`n[4/4] Testing /api/vendor-leads/metrics..." -ForegroundColor Yellow

try {
    $metricsResp = Invoke-RestMethod -Uri "$baseUrl/api/vendor-leads/metrics" `
        -Method GET `
        -Headers $authHeaders
    
    Write-Host "✅ Metrics endpoint SUCCESS (200)" -ForegroundColor Green
    Write-Host "   Response: $($metricsResp | ConvertTo-Json -Depth 1)" -ForegroundColor Gray
} catch {
    $status = 0
    try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
    
    if ($status -eq 401) {
        Write-Host "❌ Metrics endpoint FAILED: HTTP 401 (Unauthorized)" -ForegroundColor Red
        Write-Host "   Reason: User doesn't have vendor associated" -ForegroundColor DarkRed
        Write-Host "   Fix needed: Call vendorService.ensureVendorExists() in AuthController.register()" -ForegroundColor Yellow
    } else {
        Write-Host "❌ Metrics endpoint FAILED: HTTP $status" -ForegroundColor Red
    }
}

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
