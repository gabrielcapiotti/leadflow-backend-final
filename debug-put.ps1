# Test script para debugar erro 500 no PUT /settings
$baseUrl = "http://localhost:8081"
$loginUrl = "$baseUrl/auth/login"
$settingsUrl = "$baseUrl/settings"

Write-Host "Debug Settings Endpoints" -ForegroundColor Yellow

# Step 1: Get token
Write-Host "`nStep 1: Autenticando..." -ForegroundColor Cyan
$loginData = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -Uri $loginUrl `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginData `
        -UseBasicParsing -SkipCertificateCheck
    
    $loginBody = $loginResponse.Content | ConvertFrom-Json
    $token = $loginBody.token
    Write-Host "Token obtido" -ForegroundColor Green
} catch {
    Write-Host "Erro ao autenticar" -ForegroundColor Red
    exit 1
}

# Step 2: Prepare PUT request
Write-Host "`nStep 2: Preparando PUT /settings..." -ForegroundColor Cyan

$updateData = @{
    vendorName = "Test Vendor"
    whatsapp = "11999999999"
    companyName = "Test Company"
    logo = "https://example.com/logo.jpg"
    welcomeMessage = "Welcome!"
} | ConvertTo-Json

Write-Host "Corpo da requisicao:" -ForegroundColor Gray
Write-Host $updateData -ForegroundColor Gray

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

# Step 3: Execute PUT
Write-Host "`nStep 3: Executando PUT /settings..." -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri $settingsUrl `
        -Method Put `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $updateData `
        -UseBasicParsing -SkipCertificateCheck
    
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Gray
    Write-Host ($response.Content)
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $statusCode" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
            $stream.Close()
            
            Write-Host "Resposta do servidor:" -ForegroundColor Red
            Write-Host $errorBody
        } catch {
            Write-Host "Erro ao ler resposta" -ForegroundColor Red
        }
    }
}
