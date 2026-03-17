# Test script para debugar os 2 endpoints com problema

$baseUrl = "http://localhost:8081"
$loginUrl = "$baseUrl/auth/login"
$settingsUrl = "$baseUrl/settings"

# Login
Write-Host "Autenticando..." -ForegroundColor Cyan
$loginData = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -Uri $loginUrl -Method Post -ContentType "application/json" -Body $loginData -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).accessToken

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

# Criar setting
Write-Host "`nCriando setting..." -ForegroundColor Cyan
$createData = @{
    vendorName = "Test"
    whatsapp = "5511999999999"
    companyName = "Test Co"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Welcome"
} | ConvertTo-Json

$resp = Invoke-WebRequest -Uri $settingsUrl -Method Put -ContentType "application/json" -Headers $headers -Body $createData -UseBasicParsing
$settingId = ($resp.Content | ConvertFrom-Json).id
Write-Host "Setting criado: $settingId" -ForegroundColor Green

# Teste 1: GET /public/{id}
Write-Host "`n=== TESTE 1: GET /settings/public/{id} ===" -ForegroundColor Yellow
$publicUrl = "$settingsUrl/public/$settingId"
Write-Host "URL: $publicUrl" -ForegroundColor Gray

try {
    $resp = Invoke-WebRequest -Uri $publicUrl -Method Get -UseBasicParsing
    Write-Host "Status: $($resp.StatusCode)" -ForegroundColor Green
    Write-Host "Response: $($resp.Content)" -ForegroundColor Green
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $statusCode" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        $reader.Close()
        
        Write-Host "Response Body: $body" -ForegroundColor Red
    }
}

# Teste 2: POST /reset
Write-Host "`n=== TESTE 2: POST /settings/reset ===" -ForegroundColor Yellow
$resetUrl = "$settingsUrl/reset"
Write-Host "URL: $resetUrl" -ForegroundColor Gray

try {
    $resp = Invoke-WebRequest -Uri $resetUrl -Method Post -ContentType "application/json" -Headers $headers -UseBasicParsing
    Write-Host "Status: $($resp.StatusCode)" -ForegroundColor Green
    Write-Host "Response: $($resp.Content)" -ForegroundColor Green
} catch {
    $statusCodeObj = $_.Exception.Response.StatusCode
    Write-Host "Status: $($statusCodeObj)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            $reader.Close()
            
            Write-Host "Response Body:" -ForegroundColor Red
            Write-Host $body
        } catch {
            Write-Host "Erro ao ler resposta" -ForegroundColor Red
        }
    }
}
