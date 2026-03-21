param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$AdminToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjoiQURNSU4iLCJpc3MiOiJsZWFkZmxvdyIsImV4cCI6OTk5OTk5OTk5OX0.dummy"
)

# ======================================================
# GLOBAL STATE
# ======================================================
$Global:TestCount = 0
$Global:Passed = 0
$Global:Failed = 0

function Pass($msg) {
    Write-Host "[PASS] $msg" -ForegroundColor Green
    $Global:TestCount++; $Global:Passed++
}

function Fail($msg, $code="") {
    Write-Host "[FAIL] $msg (HTTP $code)" -ForegroundColor Red
    $Global:TestCount++; $Global:Failed++
}

function Summary {
    Write-Host "`n========== VENDOR MULTI-TENANT TEST SUMMARY ==========" -ForegroundColor Cyan
    Write-Host "Total: $Global:TestCount"
    Write-Host "Passed: $Global:Passed" -ForegroundColor Green
    Write-Host "Failed: $Global:Failed" -ForegroundColor Red
    Write-Host ""
    if ($Global:Failed -eq 0) {
        Write-Host "ALL TESTS PASSED - Multi-tenant vendor isolation verified! " -ForegroundColor Green
    }
    if ($Global:Failed -gt 0) { exit 1 } else { exit 0 }
}

function Headers($tenant="public") {
    return @{
        "Content-Type" = "application/json"
        "X-Tenant-Id" = $tenant
    }
}

# ======================================================
# MULTI-TENANT ISOLATION TESTS
# ======================================================

Write-Host "`nVENDOR MULTI-TENANT ISOLATION TESTS" -ForegroundColor Yellow

# Test 1: Create vendor in tenant=public
$slug1 = "vendor-test-$(Get-Random)"
$body = @{
    nomeVendedor="Public Tenant Vendor"
    whatsappVendedor="999"
    nomeEmpresa="Company A"
    slug=$slug1
} | ConvertTo-Json

try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method POST -Headers (Headers "public") -Body $body -UseBasicParsing -ErrorAction Stop
    $data = $r.Content | ConvertFrom-Json
    $vendorId1 = $data.id
    Pass "Vendor created in tenant=public (ID: $vendorId1)"
} catch {
    Fail "Failed to create vendor in public tenant" $_.Exception.Response.StatusCode
    Summary
}

# Test 2: Verify tenant_id was set correctly in database (via GET)
try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors/$vendorId1" -Method GET -Headers (Headers "public") -UseBasicParsing -ErrorAction Stop
    $vendor = $r.Content | ConvertFrom-Json
    if ($vendor.tenantId -eq "public") {
        Pass "Vendor has correct tenantId=public"
    } else {
        Fail "Vendor tenantId is wrong" $r.StatusCode
    }
} catch {
    Fail "Failed to fetch vendor" $_.Exception.Response.StatusCode
}

# Test 3: Duplicate slug should fail in same tenant
$body2 = @{
    nomeVendedor="Another Vendor"
    whatsappVendedor="888"
    nomeEmpresa="Company B"
    slug=$slug1
} | ConvertTo-Json

try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method POST -Headers (Headers "public") -Body $body2 -UseBasicParsing -ErrorAction Stop
    Fail "Duplicate slug should have been rejected"
} catch {
    if ($_.Exception.Response.StatusCode -in @(400,409)) {
        Pass "Duplicate slug blocked in same tenant (as expected)"
    } else {
        Fail "Wrong error code for duplicate slug" $_.Exception.Response.StatusCode
    }
}

# Test 4: Same slug ALLOWED in different tenant
$body3 = @{
    nomeVendedor="Different Tenant Vendor"  
    whatsappVendedor="777"
    nomeEmpresa="Company C"
    slug=$slug1
} | ConvertTo-Json

try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method POST -Headers (Headers "tenant_b") -Body $body3 -UseBasicParsing -ErrorAction Stop
    $data = $r.Content | ConvertFrom-Json
    $vendorId2 = $data.id
    Pass "Same slug allowed in different tenant (tenant_b)"
} catch {
    Fail "Failed to create vendor with same slug in different tenant" $_.Exception.Response.StatusCode
}

# Test 5: Tenant isolation on LIST
try {
    $r1 = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method GET -Headers (Headers "public") -UseBasicParsing -ErrorAction Stop
    $listPublic = $r1.Content | ConvertFrom-Json
    
    $r2 = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method GET -Headers (Headers "tenant_b") -UseBasicParsing -ErrorAction Stop
    $listTenantB = $r2.Content | ConvertFrom-Json
    
    # Convert to array
    $arrPublic = if ($listPublic -is [array]) { $listPublic } else { @($listPublic) }
    $arrTenantB = if ($listTenantB -is [array]) { $listTenantB } else { @($listTenantB) }
    
    if ($arrPublic | Where-Object {$_.id -eq $vendorId2}) {
        Fail "Tenant isolation leak: tenant_b vendor visible in public list"
    } else {
        Pass "List properly filtered by tenant (no cross-tenant leak)"
    }
} catch {
    Fail "Failed to list vendors" $_.Exception.Response.StatusCode
}

# Test 6: GET isolation - different tenant cannot access vendor
try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors/$vendorId1" -Method GET -Headers (Headers "tenant_b") -UseBasicParsing -ErrorAction Stop
    Fail "Cross-tenant GET should be blocked but succeeded"
} catch {
    if ($_.Exception.Response.StatusCode -in @(403,404)) {
        Pass "Cross-tenant GET properly blocked"
    } else {
        Fail "Wrong error for cross-tenant GET" $_.Exception.Response.StatusCode
    }
}

# Test 7: UPDATE isolation
$updateBody = @{ nomeVendedor="Hacked Vendor" } | ConvertTo-Json

try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors/$vendorId1" -Method PUT -Headers (Headers "tenant_b") -Body $updateBody -UseBasicParsing -ErrorAction Stop
    Fail "Cross-tenant UPDATE should be blocked"
} catch {
    if ($_.Exception.Response.StatusCode -in @(403,404)) {
        Pass "Cross-tenant UPDATE properly blocked"
    } else {
        Fail "Wrong error for cross-tenant UPDATE" $_.Exception.Response.StatusCode
    }
}

# Test 8: DELETE isolation
try {
    $r = Invoke-WebRequest -Uri "$BaseUrl/vendors/$vendorId1" -Method DELETE -Headers (Headers "tenant_b") -UseBasicParsing -ErrorAction Stop
    Fail "Cross-tenant DELETE should be blocked"
} catch {
    if ($_.Exception.Response.StatusCode -in @(403,404)) {
        Pass "Cross-tenant DELETE properly blocked"
    } else {
        Fail "Wrong error for cross-tenant DELETE" $_.Exception.Response.StatusCode
    }
}

# ======================================================
# FINAL
# ======================================================

Summary

