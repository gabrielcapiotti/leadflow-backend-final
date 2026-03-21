#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"
$vendorId = "4c87137d-5f4e-4a57-8c42-3df42b0d9529"

Write-Host "=== TESTE DO ENDPOINT /admin/metrics/health ===" -ForegroundColor Cyan
Write-Host "Vendor ID: $vendorId" -ForegroundColor Green
Write-Host ""

# Fazer login
Write-Host "Fazendo login..." -ForegroundColor Yellow
try {
    $login = Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method POST `
        -ContentType "application/json" `
        -Body (@{email="admin@leadflow.com"; password="Admin@Lead123"} | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Continue
    
    $token = ($login.Content | ConvertFrom-Json).accessToken
    $headers = @{
        "Authorization" = "Bearer $token"
    }
    Write-Host "Token obtido com sucesso!" -ForegroundColor Green
} catch {
    Write-Host "[ERRO] Login falhou: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Testando endpoint..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/admin/metrics/health/$vendorId" -Method GET `
        -Headers $headers `
        -UseBasicParsing -ErrorAction Continue
    
    $statusCode = $response.StatusCode
    $body = $response.Content | ConvertFrom-Json
    
    Write-Host ""
    Write-Host "Status HTTP: $statusCode" -ForegroundColor $(if ($statusCode -eq 200) { "Green" } else { "Red" })
    Write-Host ""
    Write-Host "Dados da resposta:" -ForegroundColor Cyan
    Write-Host ($body | ConvertTo-Json) -ForegroundColor Green
    
    if ($statusCode -eq 200) {
        Write-Host ""
        Write-Host "[SUCESSO] Endpoint /admin/metrics/health funcionando com vendor válido!" -ForegroundColor Green
    }
} catch {
    Write-Host ""
    Write-Host "Status HTTP: $($_.Exception.Response.StatusCode.Value)" -ForegroundColor Red
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
}
