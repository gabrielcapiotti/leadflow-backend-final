# ========================================================================
# VENDOR CONTROLLER - SIMPLIFIED TEST (Based on test-leads pattern)
# ========================================================================

$BaseUrl = "http://localhost:8081"
$RegisterUrl = "$BaseUrl/auth/register"
$LoginUrl = "$BaseUrl/auth/login"
$MeUrl = "$BaseUrl/auth/me"
$VendorsUrl = "$BaseUrl/vendors"

$Global:Passed = 0
$Global:Failed = 0
$Global:TestCount = 0

function Write-Pass {
    param([string]$Test, [int]$Code)
    Write-Host "   ✅ $Test - HTTP $Code" -ForegroundColor Green
    $Global:Passed++
}

function Write-Fail {
    param([string]$Test, [int]$Code, [string]$Msg = "")
    Write-Host "   ❌ $Test - HTTP $Code - $Msg" -ForegroundColor Red
    $Global:Failed++
}

Clear-Host
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  VENDOR CONTROLLER - SIMPLIFIED TEST SUITE" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# ========================================================================
# TEST 1: Register User
# ========================================================================
Write-Host "[TEST 1] Register User" -ForegroundColor Cyan
$TS = Get-Date -Format "yyyyMMddHHmmssfff"
$Email = "vendor_$TS@leadflow.dev"
$Pass = "SecurePassword123!"
$Name = "Test Vendor User"

try {
    $Resp = Invoke-WebRequest -Uri $RegisterUrl -Method Post -ContentType "application/json" `
        -Body (@{email=$Email; password=$Pass; confirmPassword=$Pass; name=$Name} | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    Write-Pass "Register" $Resp.StatusCode
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Register" $Code $_.Exception.Message
    $Global:TestCount++
    exit
}

# ========================================================================
# TEST 2: Login
# ========================================================================
Write-Host "[TEST 2] Login" -ForegroundColor Cyan

try {
    $Resp = Invoke-WebRequest -Uri $LoginUrl -Method Post -ContentType "application/json" `
        -Body (@{email=$Email; password=$Pass} | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    
    $Data = $Resp.Content | ConvertFrom-Json
    $Token = $Data.accessToken
    
    Write-Pass "Login" $Resp.StatusCode
    $Global:TestCount++
    
    # Wait to ensure user session is fully established
    Start-Sleep -Milliseconds 500
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Login" $Code $_.Exception.Message
    $Global:TestCount++
    exit
}

# ========================================================================
# TEST 3: Get Profile
# ========================================================================
Write-Host "[TEST 3] Get Profile" -ForegroundColor Cyan

try {
    $Headers = @{ "Authorization" = "Bearer $Token" }
    $Resp = Invoke-WebRequest -Uri $MeUrl -Method Get -Headers $Headers `
        -UseBasicParsing -ErrorAction Stop
    
    $Profile = $Resp.Content | ConvertFrom-Json
    $TenantId = $Profile.tenantId
    
    Write-Pass "Get Profile" $Resp.StatusCode
    Write-Host "   TenantId: $TenantId" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get Profile" $Code $_.Exception.Message
    $Global:TestCount++
    exit
}

# ========================================================================
# TEST 4: Create Vendor
# ========================================================================
Write-Host "[TEST 4] Create Vendor" -ForegroundColor Cyan

$VendorSlug = "vendor-$TS"
$VendorPayload = @{
    nomeVendedor = "Test Vendor"
    userEmail = $Email
    nomeEmpresa = "Test Company"
    whatsappVendedor = "+5511999999999"
    logoUrl = "https://example.com/logo.png"
    corDestaque = "#FF6B35"
    mensagemBoasVindas = "Welcome!"
    slug = $VendorSlug
}

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri $VendorsUrl -Method Post -ContentType "application/json" `
        -Headers $Headers -Body ($VendorPayload | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    
    $Vendor = $Resp.Content | ConvertFrom-Json
    $VendorId = $Vendor.id
    
    Write-Pass "Create Vendor" $Resp.StatusCode
    Write-Host "   Vendor ID: $VendorId" -ForegroundColor DarkGray
    Write-Host "   Slug: $VendorSlug" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Create Vendor" $Code $_.Exception.Message
    $Global:TestCount++
    exit
}

# ========================================================================
# TEST 5: Get Vendor by ID
# ========================================================================
Write-Host "[TEST 5] Get Vendor by ID" -ForegroundColor Cyan

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" -Method Get `
        -Headers $Headers -UseBasicParsing -ErrorAction Stop
    
    $Vendor = $Resp.Content | ConvertFrom-Json
    
    Write-Pass "Get Vendor" $Resp.StatusCode
    Write-Host "   Name: $($Vendor.nomeVendedor)" -ForegroundColor DarkGray
    Write-Host "   Company: $($Vendor.nomeEmpresa)" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get Vendor" $Code $_.Exception.Message
    $Global:TestCount++
}

# ========================================================================
# TEST 6: List Vendors
# ========================================================================
Write-Host "[TEST 6] List Vendors" -ForegroundColor Cyan

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri $VendorsUrl -Method Get `
        -Headers $Headers -UseBasicParsing -ErrorAction Stop
    
    $Vendors = $Resp.Content | ConvertFrom-Json
    
    Write-Pass "List Vendors" $Resp.StatusCode
    Write-Host "   Total: $($Vendors.Count)" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "List Vendors" $Code $_.Exception.Message
    $Global:TestCount++
}

# ========================================================================
# TEST 7: Filter by Slug
# ========================================================================
Write-Host "[TEST 7] Filter Vendors by Slug" -ForegroundColor Cyan

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri "$VendorsUrl?slug=$VendorSlug" -Method Get `
        -Headers $Headers -UseBasicParsing -ErrorAction Stop
    
    $Vendors = $Resp.Content | ConvertFrom-Json
    
    Write-Pass "Filter by Slug" $Resp.StatusCode
    Write-Host "   Results: $($Vendors.Count)" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Filter by Slug" $Code $_.Exception.Message
    $Global:TestCount++
}

# ========================================================================
# TEST 8: Update Vendor
# ========================================================================
Write-Host "[TEST 8] Update Vendor" -ForegroundColor Cyan

$UpdatePayload = @{
    nomeVendedor = "Updated Vendor Name"
    nomeEmpresa = "Updated Company"
    whatsappVendedor = "+5511888888888"
    logoUrl = "https://example.com/logo-updated.png"
    corDestaque = "#1E90FF"
    mensagemBoasVindas = "Updated Welcome!"
    slug = $VendorSlug
}

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" -Method Put -ContentType "application/json" `
        -Headers $Headers -Body ($UpdatePayload | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    
    $Updated = $Resp.Content | ConvertFrom-Json
    
    Write-Pass "Update Vendor" $Resp.StatusCode
    Write-Host "   New Name: $($Updated.nomeVendedor)" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Update Vendor" $Code $_.Exception.Message
    $Global:TestCount++
}

# ========================================================================
# TEST 9: Delete Vendor
# ========================================================================
Write-Host "[TEST 9] Delete Vendor" -ForegroundColor Cyan

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" -Method Delete `
        -Headers $Headers -UseBasicParsing -ErrorAction Stop
    
    Write-Pass "Delete Vendor" $Resp.StatusCode
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Delete Vendor" $Code $_.Exception.Message
    $Global:TestCount++
}

# ========================================================================
# TEST 10: Verify Deletion
# ========================================================================
Write-Host "[TEST 10] Verify Deletion" -ForegroundColor Cyan

try {
    $Headers = @{
        "Authorization" = "Bearer $Token"
        "X-Tenant-ID" = $TenantId
    }
    
    $Resp = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" -Method Get `
        -Headers $Headers -UseBasicParsing -ErrorAction Stop
    
    Write-Fail "Verify Deletion" $Resp.StatusCode "Vendor still exists"
    $Global:TestCount++
} catch {
    $Code = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($Code -eq 404) {
        Write-Pass "Verify Deletion (404)" 404
        $Global:TestCount++
    } else {
        Write-Fail "Verify Deletion" $Code
        $Global:TestCount++
    }
}

# ========================================================================
# SUMMARY
# ========================================================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan

$Total = $Global:Passed + $Global:Failed
$Rate = if ($Total -gt 0) { [math]::Round(($Global:Passed / $Total) * 100, 2) } else { 0 }

Write-Host "Total: $Total | Passed: $($Global:Passed) ✅ | Failed: $($Global:Failed) ❌ | Pass Rate: $Rate%" -ForegroundColor Green

if ($Global:Failed -eq 0) {
    Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "❌ Some tests failed" -ForegroundColor Red
}

Write-Host ""
