#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"
$tenantId = "public"

function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    while ($payload.Length % 4) { $payload += "=" }
    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($payload)
    ) | ConvertFrom-Json
}

Write-Host "=== VENDOR UPDATE DEBUG ===" -ForegroundColor Cyan

# 1. REGISTER & LOGIN
$email = "vendor_dbg_$(Get-Random)@test.com"
$regBody = @{
    email=$email; password="Test@123456"; confirmPassword="Test@123456"; name="Test"
} | ConvertTo-Json

$regRes = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $regBody `
    -UseBasicParsing

$loginBody = @{email=$email; password="Test@123456"} | ConvertTo-Json
$loginRes = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $loginBody `
    -UseBasicParsing

$token = ($loginRes.Content | ConvertFrom-Json).accessToken

# 2. CREATE VENDOR
Write-Host "`n1. Creating vendor..." -ForegroundColor Yellow
$vendor1Name = "InitialVendor-$(Get-Random)"
$createBody = @{
    name=$vendor1Name
    nomeVendedor="Test Vendor"
    whatsappVendedor="5511999999999"
    nomeEmpresa="Test Company"
    userEmail=$email
    slug="vendor-$(Get-Random)-debug"
} | ConvertTo-Json

$createRes = Invoke-WebRequest "$baseUrl/vendors" `
    -Method POST `
    -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $createBody `
    -UseBasicParsing

$vendorId = ($createRes.Content | ConvertFrom-Json).id
$createdData = $createRes.Content | ConvertFrom-Json

Write-Host "   Created ID: $vendorId"
Write-Host "   Created name: $($createdData.name)"
Write-Host "   Created nomeVendedor: $($createdData.nomeVendedor)"

# 3. UPDATE VENDOR
Write-Host "`n2. Updating vendor..." -ForegroundColor Yellow
$vendor2Name = "UpdatedVendor-$(Get-Random)"
$updateBody = @{
    name=$vendor2Name
    nomeVendedor="Updated Name"
    whatsappVendedor="5521111111111"
    nomeEmpresa="Updated Company"
    userEmail=$email
    slug="vendor-$(Get-Random)-debug"
} | ConvertTo-Json

Write-Host "   Update body: $updateBody" -ForegroundColor DarkGray

$updateRes = Invoke-WebRequest "$baseUrl/vendors/$vendorId" `
    -Method PUT `
    -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $updateBody `
    -UseBasicParsing

$updatedData = $updateRes.Content | ConvertFrom-Json

Write-Host "   Status: $($updateRes.StatusCode)" -ForegroundColor Green
Write-Host "   Updated name: $($updatedData.name)"
Write-Host "   Updated nomeVendedor: $($updatedData.nomeVendedor)"
Write-Host "   Updated whatsapp: $($updatedData.whatsappVendedor)"

# 4. VERIFY
Write-Host "`n3. Verifying via GET..." -ForegroundColor Yellow
$getRes = Invoke-WebRequest "$baseUrl/vendors/$vendorId" `
    -Method GET `
    -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"=$tenantId} `
    -UseBasicParsing

$verifyData = $getRes.Content | ConvertFrom-Json
Write-Host "   Verified name: $($verifyData.name)"
Write-Host "   Verified nomeVendedor: $($verifyData.nomeVendedor)"

# Check if updated
if ($updatedData.name -eq $vendor2Name -and $updatedData.nomeVendedor -eq "Updated Name") {
    Write-Host "`n✅ UPDATE SUCCESSFUL" -ForegroundColor Green
} else {
    Write-Host "`n❌ UPDATE FAILED - Values not changed" -ForegroundColor Red
    Write-Host "   Expected name: $vendor2Name, got: $($updatedData.name)"
    Write-Host "   Expected nomeVendedor: Updated Name, got: $($updatedData.nomeVendedor)"
}
