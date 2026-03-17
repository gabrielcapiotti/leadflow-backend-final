Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   SETTINGS ENDPOINTS TEST SUITE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get token first
$loginUri = "http://localhost:8081/auth/login"
$loginBody = @{
    email = "vendor1@leadflow.com"
    password = "Vendor@123"
} | ConvertTo-Json

Write-Host "🔑 Obtendo token de autenticação..." -ForegroundColor Yellow

try {
    $loginResponse = Invoke-WebRequest -Uri $loginUri -Method POST `
        -ContentType "application/json" -Body $loginBody -TimeoutSec 10
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "✅ Token obtido com sucesso!" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "❌ Erro ao fazer login: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# Test 1: GET /api/settings
Write-Host "📋 TESTE 1: GET /api/settings" -ForegroundColor Cyan
Write-Host "Descricao: Obter configuracoes do usuario autenticado" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method GET -Headers $headers -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "✅ Status: 200 OK" -ForegroundColor Green
    Write-Host "Resposta:" -ForegroundColor Yellow
    $data | ConvertTo-Json | Write-Host
} catch {
    if ($_.Exception.Response) {
        Write-Host "❌ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
    } else {
        Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "─────────────────────────────────────" -ForegroundColor Gray
Write-Host ""

# Test 2: PUT /api/settings (Create/Update)
Write-Host "✏️  TESTE 2: PUT /api/settings" -ForegroundColor Cyan
Write-Host "Descricao: Criar/atualizar configuracoes do usuario" -ForegroundColor Gray
Write-Host ""

$updateBody = @{
    vendorName = "Vendor Teste Updated"
    whatsapp = "5511988776655"
    companyName = "Empresa Teste Ltda"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Bem-vindo ao nosso servico de gerenciamento de leads!"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method PUT -Headers $headers -Body $updateBody -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "✅ Status: 200 OK" -ForegroundColor Green
    Write-Host "Resposta:" -ForegroundColor Yellow
    $data | ConvertTo-Json | Write-Host
    $settingId = $data.id
} catch {
    if ($_.Exception.Response) {
        Write-Host "❌ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
    } else {
        Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "─────────────────────────────────────" -ForegroundColor Gray
Write-Host ""

# Test 3: GET /api/settings/{id}
if ($settingId) {
    Write-Host "🔍 TESTE 3: GET /api/settings/{id}" -ForegroundColor Cyan
    Write-Host "Descricao: Obter configuracoes por ID" -ForegroundColor Gray
    Write-Host ""

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" `
            -Method GET -Headers $headers -TimeoutSec 10
        $data = $response.Content | ConvertFrom-Json
        Write-Host "✅ Status: 200 OK" -ForegroundColor Green
        Write-Host "Resposta:" -ForegroundColor Yellow
        $data | ConvertTo-Json | Write-Host
    } catch {
        if ($_.Exception.Response) {
            Write-Host "❌ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
        } else {
            Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
} else {
    Write-Host "⚠️  TESTE 3 PULADO: ID de settings nao obtido do teste anterior" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "─────────────────────────────────────" -ForegroundColor Gray
Write-Host ""

# Test 4: DELETE /api/settings
Write-Host "🗑️  TESTE 4: DELETE /api/settings" -ForegroundColor Cyan
Write-Host "Descricao: Deletar configuracoes do usuario (soft delete)" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method DELETE -Headers $headers -TimeoutSec 10
    Write-Host "✅ Status: 204 No Content" -ForegroundColor Green
    Write-Host "Settings deletadas com sucesso (soft delete)!" -ForegroundColor Green
} catch {
    if ($_.Exception.Response) {
        Write-Host "❌ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
    } else {
        Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   TESTES CONCLUIDOS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
