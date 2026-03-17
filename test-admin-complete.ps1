Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "TESTE COMPLETO DE ENDPOINTS ADMIN" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Aguardar bloqueio expirar
Write-Host "Aguardando bloqueio de brute force expirar (5 minutos)..." -ForegroundColor Yellow
Write-Host "Cancelar com Ctrl+C se necessario" -ForegroundColor Gray
Write-Host ""

for ($i = 300; $i -gt 0; $i--) {
    if ($i % 30 -eq 0) {
        Write-Host "$i segundos restantes..." -ForegroundColor Gray
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "Bloqueio expirou! Iniciando testes..." -ForegroundColor Green
Write-Host ""

$baseUrl = "http://localhost:8081"
$email = "admin@leadflow.com"
$password = "Admin@123456"
$token = $null

# ============================================
# 1. LOGIN
# ============================================
Write-Host "[1] TESTE DE LOGIN" -ForegroundColor Cyan
Write-Host "POST $baseUrl/auth/login" -ForegroundColor Yellow

try {
    $loginBody = @{
        email = $email
        password = $password
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$baseUrl/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body $loginBody `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    $token = $data.accessToken

    Write-Host "LOGIN SUCESSO" -ForegroundColor Green
    Write-Host "Token: $($token.Substring(0, 50))..." -ForegroundColor Green
    Write-Host ""

} catch {
    Write-Host "FALHA NO LOGIN" -ForegroundColor Red
    if ($_.Exception.Response) {
        $status = $_.Exception.Response.StatusCode.Value__
        Write-Host "Status: $status" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Erro: $($reader.ReadToEnd())" -ForegroundColor Red
    }
    exit 1
}

# Se nao tiver token, parar
if (-not $token) {
    Write-Host "Nenhum token obtido!" -ForegroundColor Red
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

# ============================================
# 2. GET /admin/overview
# ============================================
Write-Host "[2] GET /admin/overview" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/overview" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    Write-Host "Sucesso (200)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $data | ConvertTo-Json | ForEach-Object { Write-Host "   $_" }
    Write-Host ""

} catch {
    Write-Host "Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    Write-Host ""
}

# ============================================
# 3. GET /admin/metrics/growth
# ============================================
Write-Host "[3] GET /admin/metrics/growth" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/growth?days=30" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    Write-Host "Sucesso (200)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $data | ConvertTo-Json | ForEach-Object { Write-Host "   $_" }
    Write-Host ""

} catch {
    Write-Host "Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    Write-Host ""
}

# ============================================
# 4. GET /admin/metrics/cohorts
# ============================================
Write-Host "[4] GET /admin/metrics/cohorts" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/cohorts" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    Write-Host "Sucesso (200)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $data | ConvertTo-Json | ForEach-Object { Write-Host "   $_" }
    Write-Host ""

} catch {
    Write-Host "Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    Write-Host ""
}

# ============================================
# 5. GET /admin/metrics/forecast
# ============================================
Write-Host "[5] GET /admin/metrics/forecast" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/forecast?months=6" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json
    Write-Host "Sucesso (200)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Yellow
    $data | ConvertTo-Json | ForEach-Object { Write-Host "   $_" }
    Write-Host ""

} catch {
    Write-Host "Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    Write-Host ""
}

# ============================================
# 6. GET /admin/metrics/health
# ============================================
Write-Host "[6] GET /admin/metrics/health/{vendorId}" -ForegroundColor Cyan

try {
    # Primeiro, obter um vendor ID da base
    $vendorResponse = Invoke-WebRequest -Uri "$baseUrl/admin/overview" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 `
        -ErrorAction Stop

    $vendorData = $vendorResponse.Content | ConvertFrom-Json
    
    # Se houver vendors na resposta, testar com o primeiro
    if ($vendorData.vendorId) {
        $vendorId = $vendorData.vendorId
        Write-Host "Testando com vendorId: $vendorId" -ForegroundColor Gray
        
        $response = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/health/$vendorId" `
            -Method GET `
            -Headers $headers `
            -TimeoutSec 10 `
            -ErrorAction Stop

        $data = $response.Content | ConvertFrom-Json
        Write-Host "Sucesso (200)" -ForegroundColor Green
        Write-Host "Response:" -ForegroundColor Yellow
        $data | ConvertTo-Json | ForEach-Object { Write-Host "   $_" }
    } else {
        Write-Host "Nenhum vendor disponivel para testar" -ForegroundColor Yellow
    }
    Write-Host ""

} catch {
    Write-Host "Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    Write-Host ""
}

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "TESTE CONCLUIDO" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Cyan
