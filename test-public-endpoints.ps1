Write-Host "=== TESTE ENDPOINTS PUBLICOS ===" -ForegroundColor Cyan

# 1. Login para obter token
$loginBody = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

Write-Host "`n[1] Login para obter token..."
$loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).accessToken
Write-Host "Token obtido: ${token:0:50}..." -ForegroundColor Green

# 2. Criar um setting
$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$body = @{
    vendorName = "Public Vendor"
    whatsapp = "+5511987654321"
    companyName = "Public Company"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Welcome to public settings"
} | ConvertTo-Json

Write-Host "`n[2] Criando setting para teste publico..."
$createResp = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $headers -Body $body -UseBasicParsing
$settingId = ($createResp.Content | ConvertFrom-Json).id
Write-Host "Setting criado com ID: $settingId" -ForegroundColor Green

# 3. Acessar publicamente SEM autenticação
Write-Host "`n[3] Acessando GET /api/public/settings/{id} SEM autenticacao..."
try {
    $publicResp = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/$settingId" -Method GET -UseBasicParsing
    if ($publicResp.StatusCode -eq 200) {
        Write-Host "Status: 200 OK" -ForegroundColor Green
        $data = $publicResp.Content | ConvertFrom-Json
        Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Green
        Write-Host "Company: $($data.companyName)" -ForegroundColor Green
    }
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. Testar com ID invalido
Write-Host "`n[4] Testando GET /api/public/settings/{id} com ID invalido..."
try {
    $invalidResp = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/00000000-0000-0000-0000-000000000000" -Method GET -UseBasicParsing
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode) (esperado)" -ForegroundColor Yellow
}

Write-Host "`n✓ Todos os testes de endpoints publicos concluidos" -ForegroundColor Green
