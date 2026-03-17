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
    Write-Host "✓ Token obtido" -ForegroundColor Green
} catch {
    Write-Host "✗ Erro ao autenticar: $_" -ForegroundColor Red
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

Write-Host "Corpo da requisição:" -ForegroundColor Gray
Write-Host $updateData -ForegroundColor Gray

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

Write-Host "`nHeaders enviados:" -ForegroundColor Gray
$headers | Format-Table -AutoSize | Out-Host

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
    $response.Content | ConvertFrom-Json | ConvertTo-Json | Write-Host
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $statusCode" -ForegroundColor Red
    
    try {
        $errorBody = $_.Exception.Response.Content.ReadAsStream()
        $reader = New-Object System.IO.StreamReader($errorBody)
        $errorContent = $reader.ReadToEnd()
        $reader.Close()
        
        Write-Host "Response Body:" -ForegroundColor Red
        Write-Host $errorContent
        
        # Tentar parsear como JSON
        try {
            $errorJson = $errorContent | ConvertFrom-Json
            Write-Host "`nDetalhes do erro:" -ForegroundColor Red
            $errorJson | Format-List | Out-Host
        } catch {
            Write-Host "Resposta não é JSON válido" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Erro ao ler resposta: $_" -ForegroundColor Red
    }
}
