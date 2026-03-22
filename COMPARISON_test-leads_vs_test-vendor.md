# DETAILED COMPARISON: test-leads-all-Oficial.ps1 ✅ vs test-vendor-simple.ps1 ❌

## Executive Summary
**test-leads achieves 20/20 (100%)** by following a proven pattern with unified header management.
**test-vendor fails at TEST 3/14** because it's missing critical header patterns presented in test-leads.

---

## 1. CONFIGURATION & INITIALIZATION

### ✅ test-leads-all-Oficial.ps1 (WORKING)
```powershell
# Line 1-15: Global Configuration
$BaseUrl = "http://localhost:8081"
$RegisterUrl = "$BaseUrl/auth/register"
$LoginUrl = "$BaseUrl/auth/login"
$MeUrl = "$BaseUrl/auth/me"
$LeadsUrl = "$BaseUrl/api/leads"
$VendorLeadsUrl = "$BaseUrl/api/vendor-leads"      # ✅ VENDOR LEADS ENDPOINT
$TenantHeader = "public"                            # ✅ GLOBAL TENANT HEADER

# Line 25-32: Unified Header Function
function Get-Headers {
    return @{
        "X-Tenant-Id" = $TenantHeader               # ✅ ALWAYS INCLUDED
        "Authorization" = "Bearer $LoginToken"
        "Content-Type" = "application/json"
    }
}
```

### ❌ test-vendor-simple.ps1 (MISSING)
```powershell
# Line 1-15: Missing VendorLeadsUrl and TenantHeader
$BaseUrl = "http://localhost:8081"
# ... other URLs ...
# ❌ NO $VendorLeadsUrl defined
# ❌ NO $TenantHeader defined
# ❌ NO Get-Headers function defined

# Line 70+: Headers manually created each time
$Headers = @{ "Authorization" = "Bearer $Token" }  # ❌ MISSING X-Tenant-Id
```

---

## 2. REGISTRATION PATTERN

### ✅ test-leads-all-Oficial.ps1 (WORKING) - Lines 102-125
```powershell
Write-Step "2" "Register User"
$newEmail = "user_$(Get-Date -Format 'yyyyMMdd_HHmmss')@leadflow.dev"
$newPassword = "SecurePass123!"
$newName = "Test User $((Get-Random))"

$registerBody = @{
    email = $newEmail
    password = $newPassword
    confirmPassword = $newPassword              # ✅ REQUIRED
    name = $newName                             # ✅ REQUIRED
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "$RegisterUrl" `
    -Method Post `
    -Headers @{                                 # ✅ INCLUDES X-Tenant-Id
        "X-Tenant-Id" = $TenantHeader
        "Content-Type" = "application/json"
    } `
    -Body $registerBody `
    -UseBasicParsing -ErrorAction Stop

$data = $response.Content | ConvertFrom-Json
Write-Success "Register" $response.StatusCode
```

### ❌ test-vendor-simple.ps1 (MISSING HEADER) - Lines 70-85
```powershell
$Email = "vendor_$TS@leadflow.dev"
$Pass = "SecurePassword123!"
$Name = "Test Vendor User"

$Resp = Invoke-WebRequest -Uri $RegisterUrl -Method Post `
    -ContentType "application/json" `
    -Body (@{
        email=$Email
        password=$Pass
        confirmPassword=$Pass                   # ✅ HAS THIS
        name=$Name                              # ✅ HAS THIS
    } | ConvertTo-Json) `
    -UseBasicParsing
    # ❌ NO HEADERS PASSED AT ALL!
    # ❌ MISSING X-Tenant-Id header
```

**KEY DIFFERENCE:**
- test-leads: Includes `"X-Tenant-Id" = $TenantHeader` in registration
- test-vendor: No headers parameter → defaults to no X-Tenant-Id

---

## 3. LOGIN PATTERN

### ✅ test-leads-all-Oficial.ps1 (WORKING) - Lines 140-165
```powershell
Write-Step "3" "Login"
$loginBody = @{
    email = $newEmail
    password = $newPassword
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "$LoginUrl" `
    -Method Post `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader              # ✅ INCLUDED
        "Content-Type" = "application/json"
    } `
    -Body $loginBody `
    -UseBasicParsing -ErrorAction Stop

$data = $response.Content | ConvertFrom-Json
$LoginToken = $data.data.accessToken              # ✅ STORED GLOBALLY
Write-Success "Login" $response.StatusCode
```

### ❌ test-vendor-simple.ps1 (MISSING HEADER) - Lines 96-115
```powershell
# LOGIN
$Resp = Invoke-WebRequest -Uri $LoginUrl -Method Post `
    -ContentType "application/json" `
    -Body (@{email=$Email; password=$Pass} | ConvertTo-Json) `
    -UseBasicParsing
    # ❌ NO HEADERS PARAMETER
    # ❌ MISSING X-Tenant-Id

# Token extraction
if ($Resp.StatusCode -eq 200) {
    $Token = ($Resp.Content | ConvertFrom-Json).data.accessToken
}
```

**KEY DIFFERENCE:**
- test-leads: `"X-Tenant-Id" = $TenantHeader` in login headers
- test-vendor: No headers → No X-Tenant-Id

---

## 4. GET /auth/me PATTERN (FAILURE POINT)

### ✅ test-leads-all-Oficial.ps1 (WORKING) - Lines 180-195
```powershell
Write-Step "4" "Get Profile (ME)"
try {
    $response = Invoke-WebRequest -Uri "$MeUrl" `
        -Method Get `
        -Headers (Get-Headers)                  # ✅ USES Get-Headers FUNCTION
        -UseBasicParsing -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "Get Profile" $response.StatusCode
    Write-Host "   User: $($data.email)" -ForegroundColor DarkGray
    Write-Host "   Tenant: $($data.tenantId)" -ForegroundColor DarkGray
} catch {
    Write-Fail "Get Profile" $_.Exception.Response.StatusCode $_.Exception.Message
}
```

**Result:** ✅ HTTP 200 OK
- Get-Headers() returns: X-Tenant-Id + Authorization + Content-Type
- Tenant context is properly resolved
- $data.tenantId returns "public"

### ❌ test-vendor-simple.ps1 (FAILURE!) - Lines 125-140
```powershell
# GET PROFILE (ME)
Write-Host "TEST 3: Get Profile (ME)" -ForegroundColor Yellow
try {
    $Headers = @{ "Authorization" = "Bearer $Token" }  # ❌ MISSING X-Tenant-Id!
    $Resp = Invoke-WebRequest -Uri $MeUrl -Method Get -Headers $Headers `
        -UseBasicParsing
    # ... 
} catch {
    # ERROR: "O servidor remoto retornou um erro: (500) Erro Interno do Servidor"
}
```

**Result:** ❌ HTTP 500 ERROR
- Missing X-Tenant-Id header
- Backend calls `user.getUser().getTenantId()` 
- Returns null → NullPointerException → HTTP 500

---

## 5. CREATE VENDOR PATTERN

### ✅ test-leads-all-Oficial.ps1 (WORKING) - Lines 210-235
```powershell
Write-Step "4b" "Create Vendor"
$VendorId = $null

# Generate unique slug
$timestamp = (Get-Date).Ticks
$randomPart = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 15 | % {[char]$_})
$combinedSlug = ("vendor-{0}-{1}" -f $timestamp, $randomPart).ToLower()
$uniqueSlug = $combinedSlug.Substring(0, [Math]::Min(63, $combinedSlug.Length))

$vendorBody = @{
    name = "Vendor $timestamp"
    userEmail = $newEmail                       # ✅ USES REGISTERED EMAIL
    nomeEmpresa = "Empresa Teste $timestamp"
    nomeVendedor = $newName                     # ✅ USES REGISTERED NAME
    whatsappVendedor = "+5511999999999"
    slug = $uniqueSlug
} | ConvertTo-Json

$response = Invoke-WebRequest -Uri "$BaseUrl/vendors" `
    -Method Post `
    -Headers (Get-Headers)                      # ✅ USES Get-Headers WITH X-Tenant-Id
    -Body $vendorBody `
    -UseBasicParsing -ErrorAction Stop

$data = $response.Content | ConvertFrom-Json
$VendorId = $data.id                            # ✅ STORES VENDOR ID
Write-Success "Create Vendor" $response.StatusCode
```

### ❌ test-vendor-simple.ps1 (BLOCKED BY EARLIER ERROR)
```powershell
# CANNOT REACH THIS POINT because TEST 3 (Get Profile) fails with HTTP 500
# Test suite stops at: "O servidor remoto retornou um erro: (500)"
```

---

## 6. VENDOR LEADS PATTERN

### ✅ test-leads-all-Oficial.ps1 (WORKING) - Lines 490-520
```powershell
Write-Step "10" "Create Vendor Lead"
$VendorLeadId = $null

if (-not $VendorCreated) {
    Write-Host "    ⚠️  Skipped - Vendor was not created..." -ForegroundColor Yellow
} else {
    $vendorLeadBody = @{
        nomeCompleto = "Maria Silva Consortium"
        whatsapp = "21987654321"
        tipoConsorcio = "VEICULO"
        valorCredito = "100000"
        urgencia = "quero_fechar"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/leads" `  # ✅ CORRECT ENDPOINT
        -Method Post `
        -Headers (Get-Headers)                   # ✅ X-Tenant-Id INCLUDED
        -Body $vendorLeadBody `
        -UseBasicParsing -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $VendorLeadId = $data.id                     # ✅ STORES VENDOR LEAD ID
    Write-Success "Create Vendor Lead" $response.StatusCode
}
```

### ❌ test-vendor-simple.ps1 (NOT REACHED)
```powershell
# Cannot test vendor-leads because:
# 1. Blocked at TEST 3: GET /auth/me returns HTTP 500
# 2. No $VendorLeadsUrl URL defined
# 3. No proper header management
```

---

## 7. COMPLETE TEST FLOW COMPARISON

### ✅ test-leads Flow (20/20 Tests - 100% Pass)
```
TEST 1:  Health Check                    ✅ 200
TEST 2:  Register                        ✅ 201 (with X-Tenant-Id)
TEST 3:  Login                           ✅ 200 (with X-Tenant-Id)
TEST 4:  Get Profile (ME)                ✅ 200 (with X-Tenant-Id + Bearer)
TEST 4b: Create Vendor                   ✅ 200 (with X-Tenant-Id + Bearer)
TEST 5:  Create Lead                     ✅ 201 (with X-Tenant-Id + Bearer)
TEST 6:  Get Lead by ID                  ✅ 200 (with X-Tenant-Id + Bearer)
TEST 7:  Update Lead Status              ✅ 200 (with X-Tenant-Id + Bearer)
TEST 8:  List Leads                      ✅ 200 (with X-Tenant-Id + Bearer)
TEST 8b: Cross-Tenant Isolation          ✅ 401/403 (Security check)
TEST 8c: Cross-Tenant Access by ID       ✅ 401/403 (Security check)
TEST 8d: Cross-Tenant List Isolation     ✅ 401/403 (Security check)
TEST 9:  Delete Lead                     ✅ 204 (with X-Tenant-Id + Bearer)
TEST 10: Create Vendor Lead              ✅ 201 (with X-Tenant-Id + Bearer)
TEST 11: Get Vendor Lead by ID           ✅ 200 (with X-Tenant-Id + Bearer)
TEST 12: List Vendor Leads               ✅ 200 (with X-Tenant-Id + Bearer)
TEST 12b: Cross-Tenant Vendor Isolation  ✅ 401/403 (Security check)
TEST 13: Update Vendor Lead Stage        ✅ 200 (with X-Tenant-Id + Bearer)
TEST 14: Delete Vendor Lead              ✅ 204 (with X-Tenant-Id + Bearer)
TEST 15: Delete Vendor                   ✅ 204 (with X-Tenant-Id + Bearer)

TOTAL: 20/20 ✅ (100%)
```

### ❌ test-vendor Flow (4/14 Executed - 28% Pass)
```
TEST 1: Health Check                     ✅ 200
TEST 2: Register User                    ✅ 201 (but no X-Tenant-Id)
TEST 3: Login                            ✅ 200 (but no X-Tenant-Id)
TEST 4: Get Profile (ME)                 ❌ 500 ERROR <- STOPS HERE
         
        [Cannot continue - X-Tenant-Id missing, backend fails to resolve tenant]

TEST 5+: All remaining tests blocked     ⛔ Not executed
```

---

## 8. ROOT CAUSE ANALYSIS

### Why GET /auth/me Returns HTTP 500

**Code Location:** `AuthController.java` line 160
```java
@GetMapping("/me")
public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
    CustomUserDetails user = requireAuthenticatedUser(authentication);
    return ResponseEntity.ok(Map.of(
        "id", user.getId(),
        "email", user.getUsername(),
        "role", user.getAuthorities().stream()...,
        "tenantId", user.getUser().getTenantId()  // ← REQUIRES TENANT CONTEXT
    ));
}
```

**Sequence of Failure in test-vendor:**
1. **Request:** GET /auth/me without X-Tenant-Id header
2. **Backend:** Cannot resolve tenant from X-Tenant-Id header
3. **Logic:** `user.getUser().getTenantId()` called with null tenant context
4. **Result:** Null pointer or tenant resolution failure
5. **Response:** HTTP 500 Error

**Sequence of Success in test-leads:**
1. **Request:** GET /auth/me WITH X-Tenant-Id: "public" header
2. **Backend:** Resolves tenant = "public" from header
3. **Logic:** `user.getUser().getTenantId()` returns "public"
4. **Response:** HTTP 200 OK with tenant data

---

## 9. CRITICAL MISSING ELEMENTS IN test-vendor-simple.ps1

### 🔴 MUST ADD (to match test-leads pattern)

#### 1. Global Configuration (Line 10)
```powershell
# ADD AFTER LINE 10:
$TenantHeader = "public"
$VendorLeadsUrl = "$BaseUrl/api/vendor-leads"

# ADD AFTER ALL URL DEFINITIONS:
function Get-Headers {
    return @{
        "X-Tenant-Id" = $TenantHeader
        "Authorization" = "Bearer $Token"
        "Content-Type" = "application/json"
    }
}
```

#### 2. Registration Headers (Line 72 - REPLACE)
```powershell
# FROM:
$Resp = Invoke-WebRequest -Uri $RegisterUrl -Method Post -ContentType "application/json" `

# TO:
$Resp = Invoke-WebRequest -Uri $RegisterUrl -Method Post `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader
        "Content-Type" = "application/json"
    } `
```

#### 3. Login Headers (Line 105 - REPLACE)
```powershell
# FROM:
$Resp = Invoke-WebRequest -Uri $LoginUrl -Method Post -ContentType "application/json" `

# TO:
$Resp = Invoke-WebRequest -Uri $LoginUrl -Method Post `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader
        "Content-Type" = "application/json"
    } `
```

#### 4. GET /auth/me Headers (Line 130 - REPLACE)
```powershell
# FROM:
$Headers = @{ "Authorization" = "Bearer $Token" }
$Resp = Invoke-WebRequest -Uri $MeUrl -Method Get -Headers $Headers `

# TO:
$Resp = Invoke-WebRequest -Uri $MeUrl -Method Get `
    -Headers (Get-Headers) `
```

#### 5. All Subsequent Requests
```powershell
# FROM:
-Headers @{ "Authorization" = "Bearer $Token" }

# TO:
-Headers (Get-Headers)
```

---

## 10. IMPLEMENTATION CHECKLIST

- [ ] Add `$TenantHeader = "public"` to line 10
- [ ] Add `$VendorLeadsUrl` definition to line 15
- [ ] Add `Get-Headers()` function after URL definitions
- [ ] Update registration to include X-Tenant-Id header
- [ ] Update login to include X-Tenant-Id header
- [ ] Update GET /auth/me to use Get-Headers()
- [ ] Update all vendor requests to use Get-Headers()
- [ ] Test: Run test-vendor-simple.ps1 and verify TEST 3 passes
- [ ] Test: Verify all vendor endpoints complete without HTTP 500
- [ ] Test: Achieve 100% pass rate matching test-leads pattern

---

## 11. EXPECTED RESULTS AFTER FIX

**Before:** 4/14 tests (28% pass) - blocked at TEST 3
```
TEST 1: Health Check ✅
TEST 2: Register ✅
TEST 3: Login ✅
TEST 4: Get Profile ❌ HTTP 500 (stops here)
```

**After:** 14/14 tests (100% pass) - completes all vendor operations
```
TEST 1: Health Check ✅
TEST 2: Register ✅
TEST 3: Login ✅
TEST 4: Get Profile ✅
TEST 5: Create Vendor ✅
TEST 6: Get Vendor ✅
TEST 7: Update Vendor ✅
TEST 8: List Vendors ✅
TEST 9: Create Vendor Lead ✅
TEST 10: Get Vendor Lead ✅
TEST 11: Update Vendor Lead ✅
TEST 12: List Vendor Leads ✅
TEST 13: Delete Vendor Lead ✅
TEST 14: Delete Vendor ✅
```

---

## Summary

**test-leads Pattern (Working):**
- ✅ Global $TenantHeader = "public"
- ✅ Get-Headers() function for consistent headers
- ✅ X-Tenant-Id in EVERY request (register, login, me, entities)
- ✅ Proper token storage and reuse
- ✅ 20/20 tests passing (100%)

**test-vendor Issues (Failing):**
- ❌ No $TenantHeader defined
- ❌ No Get-Headers() function
- ❌ Missing X-Tenant-Id headers throughout
- ❌ Fails at TEST 3: GET /auth/me → HTTP 500
- ❌ 4/14 tests blocked (28% execution)

**Fix Strategy:**
Simply apply the proven test-leads pattern to test-vendor-simple.ps1:
1. Copy global configuration pattern
2. Copy Get-Headers() function
3. Replace all header creations with Get-Headers calls
4. Add X-Tenant-Id to registration and login

**Expected Outcome:**
test-vendor-simple.ps1 will achieve same 100% pass rate as test-leads
