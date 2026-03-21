param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$TenantId = "public"
)

# ======================================================
# GLOBAL STATE
# ======================================================
$Global:TestCount = 0
$Global:Passed = 0
$Global:Failed = 0
$Global:Token = $null
$Global:VendorIds = @()

# Unique test data
$timestamp = Get-Date -Format "yyyyMMddHHmmssfff"
$Email = "vendor_test_$timestamp@leadflow.dev"
$Password = "Test@123456"

$Slug1 = "vendor-$([guid]::NewGuid().ToString().Substring(0,8))"
$Slug2 = "vendor-$([guid]::NewGuid().ToString().Substring(0,8))"
$Slug3 = "vendor-$([guid]::NewGuid().ToString().Substring(0,8))"

# ======================================================
# HELPERS
# ======================================================

function Pass($msg) {
    Write-Host "[PASS] $msg" -ForegroundColor Green
    $Global:TestCount++; $Global:Passed++
}

function Fail($msg, $expected="", $actual="") {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    if ($expected) { Write-Host "   Expected: $expected" -ForegroundColor DarkRed }
    if ($actual)   { Write-Host "   Actual:   $actual" -ForegroundColor DarkRed }
    $Global:TestCount++; $Global:Failed++
}

function Summary {
    Write-Host "`n========== SUMMARY ==========" -ForegroundColor Cyan
    Write-Host "Total: $Global:TestCount"
    Write-Host "Passed: $Global:Passed" -ForegroundColor Green
    Write-Host "Failed: $Global:Failed" -ForegroundColor Red
    if ($Global:Failed -gt 0) { exit 1 } else { exit 0 }
}

function Headers($token=$null, $tenant=$TenantId) {
    $h = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id" = $tenant
    }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    return $h
}

function Call($method, $url, $headers, $body=$null) {
    try {
        $params = @{
            Uri = $url
            Method = $method
            Headers = $headers
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }
        if ($body) { $params["Body"] = $body }
        $response = Invoke-WebRequest @params
        return $response
    }
    catch {
        $response = $_.Exception.Response
        if ($response) {
            # Try to read error body
            $stream = $response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
            if ($errorBody) {
                Write-Host "  DEBUG ERROR BODY: $errorBody" -ForegroundColor Yellow
            }
        }
        return $response
    }
}

function AsArray($obj) {
    if ($null -eq $obj) { return @() }
    if ($obj -is [array]) { return $obj }
    return @($obj)
}

# ======================================================
# PHASE 1 - AUTH
# ======================================================

Write-Host "`nAUTH SETUP" -ForegroundColor Yellow

# Health
$r = Call GET "$BaseUrl/actuator/health" (Headers)
if ($r -and $r.StatusCode -eq 200) { Pass "Health OK" } else { Fail "Health failed"; Summary }

# Register
$body = @{ email=$Email; password=$Password; confirmPassword=$Password; name="Test" } | ConvertTo-Json
$r = Call POST "$BaseUrl/auth/register" (Headers) $body
if ($r.StatusCode -in @(200,201)) { Pass "Register OK" } else { Fail "Register failed"; Summary }

# Login
$body = @{ email=$Email; password=$Password } | ConvertTo-Json
$r = Call POST "$BaseUrl/auth/login" (Headers) $body
if ($r.StatusCode -eq 200) {
    $loginData = $r.Content | ConvertFrom-Json
    Write-Host "DEBUG: Login response keys: $($loginData | Get-Member -MemberType NoteProperty | Select-Object -ExpandProperty Name)" -ForegroundColor Yellow
    # Try both possible field names
    $Global:Token = $loginData.accessToken
    if (-not $Global:Token) { $Global:Token = $loginData.token }
    if ($Global:Token) {
        Pass "Login OK (Token: $($Global:Token.Substring(0,20))...)"
    } else {
        Fail "Login failed (no token field)"
        Summary
    }
} else { Fail "Login failed"; Summary }

# ======================================================
# PHASE 2 - CREATE
# ======================================================

Write-Host "`nCREATE TESTS" -ForegroundColor Yellow

function CreateVendor($slug) {
    $body = @{
        nomeVendedor="Test"
        whatsappVendedor="999"
        nomeEmpresa="Company"
        slug=$slug
    } | ConvertTo-Json

    return Call POST "$BaseUrl/vendors" (Headers $Global:Token) $body
}

# Create 3 vendors
foreach ($slug in @($Slug1,$Slug2,$Slug3)) {
    $r = CreateVendor $slug
    if ($r.StatusCode -eq 200) {
        $data = $r.Content | ConvertFrom-Json
        $Global:VendorIds += $data.id
        Pass "Vendor created ($slug)"
    } else {
        Fail "Create vendor failed" "200" $r.StatusCode
    }
}

# Duplicate slug
$r = CreateVendor $Slug1
if ($r.StatusCode -in @(400,409)) { Pass "Duplicate blocked" }
else { Fail "Duplicate should fail" }

# No token
$r = Call POST "$BaseUrl/vendors" (Headers $null) "{}"
if ($r.StatusCode -eq 401) { Pass "No token blocked" }
else { Fail "No token should be 401" }

# ======================================================
# PHASE 3 - READ
# ======================================================

Write-Host "`nREAD TESTS" -ForegroundColor Yellow

$r = Call GET "$BaseUrl/vendors" (Headers $Global:Token)
if ($r.StatusCode -eq 200) {
    $list = AsArray ($r.Content | ConvertFrom-Json)
    if ($list.Count -ge 2) { Pass "List OK" }
    else { Fail "List too small" }
} else { Fail "List failed" }

# ======================================================
# PHASE 4 - UPDATE
# ======================================================

Write-Host "`nUPDATE TESTS" -ForegroundColor Yellow

$id = $Global:VendorIds[0]

$body = @{ nomeVendedor="Updated"; slug=$Slug1 } | ConvertTo-Json
$r = Call PUT "$BaseUrl/vendors/$id" (Headers $Global:Token) $body

if ($r.StatusCode -eq 200) {
    $data = $r.Content | ConvertFrom-Json
    if ($data.nomeVendedor -eq "Updated") { Pass "Update OK" }
    else { Fail "Update failed (value)" }
} else { Fail "Update failed (status)" }

# ======================================================
# PHASE 5 - DELETE
# ======================================================

Write-Host "`nDELETE TESTS" -ForegroundColor Yellow

$id = $Global:VendorIds[2]
$r = Call DELETE "$BaseUrl/vendors/$id" (Headers $Global:Token)

if ($r.StatusCode -in @(200,204)) { Pass "Delete OK" }
else { Fail "Delete failed" }

# Validate delete
$r = Call GET "$BaseUrl/vendors?slug=$Slug3" (Headers $Global:Token)
$list = AsArray ($r.Content | ConvertFrom-Json)

if ($list.Count -eq 0) { Pass "Delete confirmed" }
else { Fail "Delete not effective" }

# ======================================================
# PHASE 6 - SECURITY
# ======================================================

Write-Host "`nSECURITY TESTS" -ForegroundColor Yellow

$otherTenant = Headers $Global:Token "tenant_hacker"

# List isolation
$r = Call GET "$BaseUrl/vendors" $otherTenant
$list = AsArray ($r.Content | ConvertFrom-Json)

if ($r.StatusCode -eq 403 -or $list.Count -eq 0) {
    Pass "Cross-tenant list blocked"
} else {
    Fail "Cross-tenant list leak"
}

# ID isolation
$id = $Global:VendorIds[0]
$r = Call GET "$BaseUrl/vendors/$id" $otherTenant

if ($r.StatusCode -in @(403,404)) {
    Pass "Cross-tenant GET blocked"
} else {
    Fail "Cross-tenant GET leak"
}

# Update isolation
$r = Call PUT "$BaseUrl/vendors/$id" $otherTenant "{}"
if ($r.StatusCode -in @(403,404)) {
    Pass "Cross-tenant update blocked"
} else {
    Fail "Cross-tenant update leak"
}

# Delete isolation
$r = Call DELETE "$BaseUrl/vendors/$id" $otherTenant
if ($r.StatusCode -in @(403,404)) {
    Pass "Cross-tenant delete blocked"
} else {
    Fail "Cross-tenant delete leak"
}

# ======================================================
# CLEANUP
# ======================================================

Write-Host "`nCLEANUP" -ForegroundColor Yellow

foreach ($id in $Global:VendorIds) {
    Call DELETE "$BaseUrl/vendors/$id" (Headers $Global:Token) | Out-Null
}

# ======================================================
# FINAL
# ======================================================

Summary