$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

Write-Host "`nDEBUG TEST - CAPTURING ERROR DETAILS`n" -ForegroundColor Cyan

# Register and login
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$email = "test-$timestamp@leadflow.dev"
$password = "SecurePass123!@"

$r = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method POST `
  -Headers @{"Content-Type" = "application/json"; "X-Tenant-Id" = $TenantHeader} `
  -Body (ConvertTo-Json @{
    name = "Test"
    email = $email
    password = $password
    confirmPassword = $password
  })

$AccessToken = $r.accessToken
Write-Host "1. Registered: $email" -ForegroundColor Green
Write-Host "   Token: $($AccessToken.Substring(0,30))..." -ForegroundColor DarkGray

# Check if Vendor was created
Write-Host "`n2. Checking Vendor for: $email" -ForegroundColor Yellow
try {
  $vendorResp = Invoke-RestMethod -Uri "$BaseUrl/vendors?user_email=$email" -Method GET `
    -Headers @{"Content-Type" = "application/json"; "X-Tenant-Id" = $TenantHeader} `
    -ErrorAction Stop
  
  if ($vendorResp -and $vendorResp.Count -gt 0) {
    Write-Host "   Vendor FOUND: $($vendorResp[0] | ConvertTo-Json -Compress)" -ForegroundColor Green
  } else {
    Write-Host "   Vendor NOT FOUND (empty list)" -ForegroundColor Red
  }
} catch {
  Write-Host "   Error querying vendors: $($_.Exception.Message)" -ForegroundColor Red
}

# Try to list vendor leads (this should fail with detailed error)
Write-Host "`n3. Attempting to list vendor leads" -ForegroundColor Yellow
try {
  $response = Invoke-RestMethod -Uri "$BaseUrl/vendor-leads?page=0&size=10" -Method GET `
    -Headers @{
      "Authorization" = "Bearer $AccessToken"
      "Content-Type" = "application/json"
      "X-Tenant-Id" = $TenantHeader
    } `
    -ErrorAction Stop
  Write-Host "   Success: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
  $errorResponse = $_.Exception.Response
  $statusCode = if ($errorResponse) { $errorResponse.StatusCode.value__ } else { "Unknown" }
  
  Write-Host "   HTTP $statusCode" -ForegroundColor Red
  
  try {
    $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
    $body = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "   Response Body: $body" -ForegroundColor DarkRed
    
    $jsonBody = $body | ConvertFrom-Json -ErrorAction SilentlyContinue
    if ($jsonBody) {
      Write-Host "   Parsed JSON:" -ForegroundColor DarkRed
      $jsonBody | Format-List
    }
  } catch {}
}

# Try to create vendor lead (this should fail with detailed error)
Write-Host "`n4. Attempting to create vendor lead" -ForegroundColor Yellow
try {
  $response = Invoke-RestMethod -Uri "$BaseUrl/vendor-leads/leads" -Method POST `
    -Headers @{
      "Authorization" = "Bearer $AccessToken"
      "Content-Type" = "application/json"
      "X-Tenant-Id" = $TenantHeader
    } `
    -Body (ConvertTo-Json @{
      nomeCompleto = "Maria"
      whatsapp = "999999999"
      tipoConsorcio = "VEICULO"
      valorCredito = "100000"
      urgencia = "medio"
    }) `
    -ErrorAction Stop
  Write-Host "   Success: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
  $errorResponse = $_.Exception.Response
  $statusCode = if ($errorResponse) { $errorResponse.StatusCode.value__ } else { "Unknown" }
  
  Write-Host "   HTTP $statusCode" -ForegroundColor Red
  
  try {
    $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
    $body = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "   Response Body: $body" -ForegroundColor DarkRed
    
    $jsonBody = $body | ConvertFrom-Json -ErrorAction SilentlyContinue
    if ($jsonBody) {
      Write-Host "   Parsed JSON:" -ForegroundColor DarkRed
      $jsonBody | Format-List
    }
  } catch {}
}
