# Quick debug script for TEST 6 and 14

$BaseURL = "http://localhost:8081"
$TenantHeader = "public"

# Setup from previous test
$uniqueSuffix = Get-Date -Format "yyyyMMddHHmmssf"
$userEmail = "vendor_user_$uniqueSuffix@leadflow.dev"
$userPassword = "SecurePassword123!"

# Register
$response = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
    -Method POST `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader
        "Content-Type" = "application/json"
    } `
    -Body (@{
        email = $userEmail
        password = $userPassword
        confirmPassword = $userPassword
        name = "Vendor Test User"
    } | ConvertTo-Json) `
    -UseBasicParsing

# Login
$loginResponse = Invoke-WebRequest -Uri "$BaseURL/auth/login" `
    -Method POST `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader
        "Content-Type" = "application/json"
    } `
    -Body (@{
        email = $userEmail
        password = $userPassword
    } | ConvertTo-Json) `
    -UseBasicParsing

$loginData = $loginResponse.Content | ConvertFrom-Json
$AuthToken = $loginData.accessToken

# Get Profile
$profileResponse = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
    -Method GET `
    -Headers @{
        "X-Tenant-Id" = $TenantHeader
        "Authorization" = "Bearer $AuthToken"
        "Content-Type" = "application/json"
    } `
    -UseBasicParsing

$profileData = $profileResponse.Content | ConvertFrom-Json
$TenantId = $profileData.tenantId

# Create Vendor
$vendorSlug = "vendor-test-" + $uniqueSuffix + "-" + (-join((65..90) + (97..122) | Get-Random -Count 8 | ForEach {[char]$_}))

$vendorData = @{
    name = "Vendor $uniqueSuffix"
    nomeVendedor = "Gabriel Capiotti"
    userEmail = $userEmail
    nomeEmpresa = "Tech Solutions"
    whatsappVendedor = "+5511987654321"
    logoUrl = "https://example.com/logo.png"
    corDestaque = "#FF6B35"
    mensagemBoasVindas = "Bem-vindo à Tech Solutions!"
    slug = $vendorSlug
}

$createResponse = Invoke-WebRequest -Uri "$BaseURL/vendors" `
    -Method POST `
    -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $AuthToken"; "X-Tenant-ID" = $TenantId } `
    -Body ($vendorData | ConvertTo-Json) `
    -UseBasicParsing

$vendorResponse = $createResponse.Content | ConvertFrom-Json
$vendorId = $vendorResponse.id

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TEST 6: Get Vendor by ID" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Vendor ID: $vendorId" -ForegroundColor Yellow
Write-Host "URL: $BaseURL/vendors/$vendorId" -ForegroundColor Yellow

try {
    $getResponse = Invoke-WebRequest -Uri "$BaseURL/vendors/$vendorId" `
        -Method GET `
        -Headers @{ Authorization = "Bearer $AuthToken"; "X-Tenant-ID" = $TenantId } `
        -UseBasicParsing

    Write-Host "✅ SUCCESS (HTTP $($getResponse.StatusCode))" -ForegroundColor Green
    Write-Host "Response: $($getResponse.Content)" -ForegroundColor Green
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Host "❌ FAILED (HTTP $statusCode)" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    
    # Try to extract error body
    try {
        $errorStream = $_.Exception.Response.GetResponseStream()
        $streamReader = New-Object System.IO.StreamReader($errorStream)
        $errorBody = $streamReader.ReadToEnd()
        $streamReader.Dispose()
        Write-Host "Error Body: $errorBody" -ForegroundColor DarkRed
    } catch {
        Write-Host "Could not extract error body" -ForegroundColor Yellow
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST 13: Delete Vendor" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

try {
    $deleteResponse = Invoke-WebRequest -Uri "$BaseURL/vendors/$vendorId" `
        -Method DELETE `
        -Headers @{ Authorization = "Bearer $AuthToken"; "X-Tenant-ID" = $TenantId } `
        -UseBasicParsing

    Write-Host "✅ DELETE SUCCESS (HTTP $($deleteResponse.StatusCode))" -ForegroundColor Green
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Host "❌ DELETE FAILED (HTTP $statusCode)" -ForegroundColor Red
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TEST 14: Verify Vendor Deletion" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Attempting to GET deleted vendor: $vendorId" -ForegroundColor Yellow

try {
    $verifyResponse = Invoke-WebRequest -Uri "$BaseURL/vendors/$vendorId" `
        -Method GET `
        -Headers @{ Authorization = "Bearer $AuthToken"; "X-Tenant-ID" = $TenantId } `
        -UseBasicParsing

    if ($verifyResponse.StatusCode -eq 404) {
        Write-Host "✅ SUCCESS - Got 404 (vendor not found)" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Got HTTP $($verifyResponse.StatusCode) instead of 404" -ForegroundColor Yellow
    }
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Host "Got HTTP $statusCode when retrieving deleted vendor" -ForegroundColor Yellow
    
    if ($statusCode -eq 404) {
        Write-Host "✅ SUCCESS - 404 is correct for deleted resource" -ForegroundColor Green
    } elseif ($statusCode -eq 500) {
        Write-Host "❌ FAILED - Backend error (HTTP 500)" -ForegroundColor Red
        
        try {
            $errorStream = $_.Exception.Response.GetResponseStream()
            $streamReader = New-Object System.IO.StreamReader($errorStream)
            $errorBody = $streamReader.ReadToEnd()
            $streamReader.Dispose()
            Write-Host "Error Body: $errorBody" -ForegroundColor DarkRed
        } catch {
            Write-Host "Could not extract error body" -ForegroundColor Yellow
        }
    }
}
