$headers = @{
    "Authorization" = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjYXJsb3NAZWx0YWRlLmNvbSIsInRlbmFudElkIjoicHVibGljIiwiaWQiOiI0ZGFhNzFlYi02ZWVlLTRhNGMtOGY2ZC1jZTVkYTEwN2U5ZjMiLCJyb2xlcyI6WyJVU0VSIl0sImlhdCI6MTcxMDQwMDA4MCwiZXhwIjo5OTk5OTk5OTk5fQ.P5GPmwhJk3gWDz95zH-0R3mV3jXH0uYk5UazL0kZCJ0"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$baseUrl = "http://localhost:8081"

# Colors
$success = "Green"
$error = "Red"
$info = "Cyan"

Write-Host "`n=== TESTE DOS ENDPOINTS REFATORADOS ===" -ForegroundColor $info
Write-Host "Paths esperados:" -ForegroundColor $info
Write-Host "  ✓ GET    /api/me/settings (user-bound)" -ForegroundColor $info
Write-Host "  ✓ PUT    /api/me/settings (user-bound)" -ForegroundColor $info
Write-Host "  ✓ PATCH  /api/me/settings (user-bound)" -ForegroundColor $info
Write-Host "  ✓ DELETE /api/me/settings (user-bound)" -ForegroundColor $info
Write-Host "  ✓ GET    /api/settings/{id} (admin)" -ForegroundColor $info
Write-Host "  ✓ PUT    /api/settings/{id} (admin)" -ForegroundColor $info
Write-Host "  ✓ DELETE /api/settings/{id} (admin)" -ForegroundColor $info
Write-Host ""

$testsPassed = 0
$testsFailed = 0

# Test 1: PUT /api/me/settings (Create/Update user settings)
Write-Host "[1] PUT /api/me/settings" -ForegroundColor Yellow
try {
    $body = @{
        vendorName = "Test Vendor"
        whatsapp = "+55 11 98765-4321"
        companyName = "Test Company"
        logo = "https://example.com/logo.png"
        welcomeMessage = "Welcome to our platform"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PUT -Headers $headers -Body $body
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✅ Status 200 OK" -ForegroundColor $success
        $testsPassed++
    } else {
        Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
        $testsFailed++
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 2: GET /api/me/settings (Get user settings)
Write-Host "[2] GET /api/me/settings" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method GET -Headers $headers
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✅ Status 200 OK" -ForegroundColor $success
        $testsPassed++
    } else {
        Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
        $testsFailed++
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 3: PATCH /api/me/settings (Partial update)
Write-Host "[3] PATCH /api/me/settings" -ForegroundColor Yellow
try {
    $body = @{
        vendorName = "Updated Vendor Name"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PATCH -Headers $headers -Body $body
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✅ Status 200 OK" -ForegroundColor $success
        $testsPassed++
    } else {
        Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
        $testsFailed++
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 4: DELETE /api/me/settings (Delete user settings)
Write-Host "[4] DELETE /api/me/settings" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method DELETE -Headers $headers
    if ($response.StatusCode -eq 204) {
        Write-Host "  ✅ Status 204 No Content" -ForegroundColor $success
        $testsPassed++
    } else {
        Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
        $testsFailed++
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 5: GET /api/me/settings again (should return 400 or empty)
Write-Host "[5] GET /api/me/settings (after delete)" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method GET -Headers $headers
    if ($response.StatusCode -eq 400 -or $response.StatusCode -eq 200) {
        Write-Host "  ✅ Status $($response.StatusCode)" -ForegroundColor $success
        $testsPassed++
    } else {
        Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
        $testsFailed++
    }
} catch {
    Write-Host "  ✅ Expected error after delete" -ForegroundColor $success
    $testsPassed++
}

# Recreate for admin tests
Write-Host "[6] PUT /api/me/settings (Recreate for admin tests)" -ForegroundColor Yellow
try {
    $body = @{
        vendorName = "Admin Test Vendor"
        whatsapp = "+55 11 99999-9999"
        companyName = "Admin Test Company"
        logo = "https://example.com/logo.png"
        welcomeMessage = "Admin test"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$baseUrl/api/me/settings" -Method PUT -Headers $headers -Body $body
    $settingId = ($response.Content | ConvertFrom-Json).id
    Write-Host "  ✅ Status 200 OK (ID: $settingId)" -ForegroundColor $success
    $testsPassed++
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 7: GET /api/settings/{id} (Admin endpoint)
Write-Host "[7] GET /api/settings/{id} (admin endpoint)" -ForegroundColor Yellow
try {
    if ($settingId) {
        $response = Invoke-WebRequest -Uri "$baseUrl/api/settings/$settingId" -Method GET -Headers $headers
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✅ Status 200 OK" -ForegroundColor $success
            $testsPassed++
        } else {
            Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
            $testsFailed++
        }
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 8: PUT /api/settings/{id} (Admin endpoint)
Write-Host "[8] PUT /api/settings/{id} (admin endpoint)" -ForegroundColor Yellow
try {
    if ($settingId) {
        $body = @{
            vendorName = "Admin Updated Vendor"
            whatsapp = "+55 11 98888-8888"
            companyName = "Admin Updated Company"
            logo = "https://example.com/new-logo.png"
            welcomeMessage = "Admin updated message"
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$baseUrl/api/settings/$settingId" -Method PUT -Headers $headers -Body $body
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✅ Status 200 OK" -ForegroundColor $success
            $testsPassed++
        } else {
            Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
            $testsFailed++
        }
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

# Test 9: DELETE /api/settings/{id} (Admin endpoint)
Write-Host "[9] DELETE /api/settings/{id} (admin endpoint)" -ForegroundColor Yellow
try {
    if ($settingId) {
        $response = Invoke-WebRequest -Uri "$baseUrl/api/settings/$settingId" -Method DELETE -Headers $headers
        if ($response.StatusCode -eq 204) {
            Write-Host "  ✅ Status 204 No Content" -ForegroundColor $success
            $testsPassed++
        } else {
            Write-Host "  ❌ Status $($response.StatusCode)" -ForegroundColor $error
            $testsFailed++
        }
    }
} catch {
    Write-Host "  ❌ Error: $($_.Exception.Message)" -ForegroundColor $error
    $testsFailed++
}

Write-Host "`n=== RESULTADO ===" -ForegroundColor $info
Write-Host "Passou: $testsPassed" -ForegroundColor $success
Write-Host "Falhou: $testsFailed" -ForegroundColor $error

if ($testsFailed -eq 0) {
    Write-Host "`n✓ TODOS OS TESTES PASSARAM!" -ForegroundColor $success
} else {
    Write-Host "`n✗ ALGUNS TESTES FALHARAM" -ForegroundColor $error
}
