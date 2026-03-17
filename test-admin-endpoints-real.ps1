# Script para testar endpoints de admin com dados reais

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    TESTE DE ENDPOINTS ADMIN" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Login como admin
Write-Host "[1/6] Fazendo login como admin..." -ForegroundColor Yellow
$adminLogin = @{
    email = "admin@leadflow.com"
    password = "AdminPassword@123"
} | ConvertTo-Json

try {
    $loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" `
        -Method POST `
        -Headers @{"Content-Type"="application/json"} `
        -Body $adminLogin
    
    $loginData = $loginResp.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "✅ Login bem-sucedido!" -ForegroundColor Green
    Write-Host "Token: $($token.Substring(0, 50))..." -ForegroundColor Cyan
    Write-Host ""
} catch {
    Write-Host "❌ Erro no login: $_" -ForegroundColor Red
    exit
}

# Headers para requisições autenticadas
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# 2. GET /admin/overview
Write-Host "[2/6] Testando GET /admin/overview..." -ForegroundColor Yellow
try {
    $overviewResp = Invoke-WebRequest -Uri "http://localhost:8081/admin/overview" `
        -Method GET `
        -Headers $headers
    
    $overviewData = $overviewResp.Content | ConvertFrom-Json
    Write-Host "✅ Overview carregado com sucesso!" -ForegroundColor Green
    Write-Host "Dados:" -ForegroundColor Cyan
    $overviewData | ConvertTo-Json | Write-Host
    Write-Host ""
} catch {
    Write-Host "❌ Erro: $_" -ForegroundColor Red
}

# 3. GET /admin/metrics/growth
Write-Host "[3/6] Testando GET /admin/metrics/growth?days=30..." -ForegroundColor Yellow
try {
    $growthResp = Invoke-WebRequest -Uri "http://localhost:8081/admin/metrics/growth?days=30" `
        -Method GET `
        -Headers $headers
    
    $growthData = $growthResp.Content | ConvertFrom-Json
    Write-Host "✅ Métricas de crescimento carregadas!" -ForegroundColor Green
    Write-Host "Período: $($growthData.period)" -ForegroundColor Cyan
    Write-Host "Crescimento de Vendors: $($growthData.vendorGrowth * 100)%" -ForegroundColor Cyan
    Write-Host "Crescimento de Leads: $($growthData.leadGrowth * 100)%" -ForegroundColor Cyan
    Write-Host "Crescimento de Revenue: $($growthData.revenueGrowth * 100)%" -ForegroundColor Cyan
    Write-Host "Dados completos:" -ForegroundColor Cyan
    $growthData | ConvertTo-Json | Write-Host
    Write-Host ""
} catch {
    Write-Host "❌ Erro: $_" -ForegroundColor Red
}

# 4. GET /admin/metrics/cohorts
Write-Host "[4/6] Testando GET /admin/metrics/cohorts..." -ForegroundColor Yellow
try {
    $cohortsResp = Invoke-WebRequest -Uri "http://localhost:8081/admin/metrics/cohorts" `
        -Method GET `
        -Headers $headers
    
    $cohortsData = $cohortsResp.Content | ConvertFrom-Json
    Write-Host "✅ Análise de coortes carregada!" -ForegroundColor Green
    Write-Host "Total de coortes: $($cohortsData.Count)" -ForegroundColor Cyan
    Write-Host "Dados:" -ForegroundColor Cyan
    $cohortsData | ConvertTo-Json | Write-Host
    Write-Host ""
} catch {
    Write-Host "❌ Erro: $_" -ForegroundColor Red
}

# 5. GET /admin/metrics/forecast
Write-Host "[5/6] Testando GET /admin/metrics/forecast?months=6..." -ForegroundColor Yellow
try {
    $forecastResp = Invoke-WebRequest -Uri "http://localhost:8081/admin/metrics/forecast?months=6" `
        -Method GET `
        -Headers $headers
    
    $forecastData = $forecastResp.Content | ConvertFrom-Json
    Write-Host "✅ Previsão de MRR carregada!" -ForegroundColor Green
    Write-Host "Total de meses previstos: $($forecastData.Count)" -ForegroundColor Cyan
    Write-Host "Dados:" -ForegroundColor Cyan
    $forecastData | ConvertTo-Json | Write-Host
    Write-Host ""
} catch {
    Write-Host "❌ Erro: $_" -ForegroundColor Red
}

# 6. GET /admin/metrics/health/{vendorId}
Write-Host "[6/6] Testando GET /admin/metrics/health/{vendorId}..." -ForegroundColor Yellow
Write-Host "Nota: Este teste requer um vendorId válido do database" -ForegroundColor DarkYellow
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    TESTES CONCLUÍDOS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
