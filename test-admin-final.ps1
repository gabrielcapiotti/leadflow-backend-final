#!/usr/bin/env pwsh

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   TESTE ENDPOINTS ADMIN - DADOS REAIS" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configurações
$baseUrl = "http://localhost:8081"
$adminEmail = "admin@leadflow.com"
$adminPassword = "AdminPassword@123"

Write-Host "[1/5] Autenticando..." -ForegroundColor Yellow

try {
    # Login
    $loginBody = @{
        email = $adminEmail
        password = $adminPassword
    } | ConvertTo-Json

    $loginRequest = @{
        Uri = "$baseUrl/auth/login"
        Method = "POST"
        ContentType = "application/json"
        Body = $loginBody
        TimeoutSec = 10
    }

    $loginResp = Invoke-WebRequest @loginRequest
    $loginData = $loginResp.Content | ConvertFrom-Json
    $token = $loginData.accessToken

    Write-Host "✅ Login bem-sucedido!" -ForegroundColor Green
    
    $headers = @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    }

    # 2. GET /admin/overview
    Write-Host "[2/5] Testando /admin/overview..." -ForegroundColor Yellow
    $overviewResp = Invoke-WebRequest -Uri "$baseUrl/admin/overview" -Method GET -Headers $headers -TimeoutSec 10
    $overview = $overviewResp.Content | ConvertFrom-Json
    Write-Host "✅ Overview:" -ForegroundColor Green
    Write-Host "   Vendors: $($overview.totalVendors), Leads: $($overview.totalLeads), Revenue: \$$($overview.totalRevenue)" -ForegroundColor Cyan

    # 3. GET /admin/metrics/growth
    Write-Host "[3/5] Testando /admin/metrics/growth..." -ForegroundColor Yellow
    $growthResp = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/growth?days=30" -Method GET -Headers $headers -TimeoutSec 10
    $growth = $growthResp.Content | ConvertFrom-Json
    Write-Host "✅ Growth (30 dias):" -ForegroundColor Green
    Write-Host "   Vendors: +$([math]::Round($growth.vendorGrowth * 100, 1))%, Leads: +$([math]::Round($growth.leadGrowth * 100, 1))%" -ForegroundColor Cyan

    # 4. GET /admin/metrics/cohorts
    Write-Host "[4/5] Testando /admin/metrics/cohorts..." -ForegroundColor Yellow
    $cohortsResp = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/cohorts" -Method GET -Headers $headers -TimeoutSec 10
    $cohorts = $cohortsResp.Content | ConvertFrom-Json
    Write-Host "✅ Cohorts: $($cohorts.Count) encontradas" -ForegroundColor Green

    # 5. GET /admin/metrics/forecast
    Write-Host "[5/5] Testando /admin/metrics/forecast..." -ForegroundColor Yellow
    $forecastResp = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/forecast?months=6" -Method GET -Headers $headers -TimeoutSec 10
    $forecast = $forecastResp.Content | ConvertFrom-Json
    Write-Host "✅ Forecast: $($forecast.Count) meses previstos" -ForegroundColor Green

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "   ✅ TODOS OS TESTES PASSARAM!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan

} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Response) {
        Write-Host "Status Code: $($_.Response.StatusCode)" -ForegroundColor Red
    }
}
