#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"

# Usar credenciais que já sabemos estar funcionando
$adminEmail = "admin@leadflow.com"
$adminPassword = "Admin@Lead123"

Write-Host "=== TESTE DO ENDPOINT /admin/metrics/health ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Buscar vendor ID válido do banco
Write-Host "Step 1: Buscando vendor ID válido..." -ForegroundColor Yellow
$env:PGPASSWORD = "venusia"
$vendorId = (psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT id FROM vendors WHERE subscription_status = 'ATIVA' LIMIT 1;") | ForEach-Object {$_.Trim()} | Where-Object {$_ -ne "" -and $_.Length -gt 10} | Select-Object -First 1

if (-not $vendorId) {
    Write-Host "[ERRO] Nenhum vendor encontrado!" -ForegroundColor Red
    exit 1
}
Write-Host "Vendor ID: $vendorId" -ForegroundColor Green

# Step 2: Fazer login
Write-Host ""
Write-Host "Step 2: Fazendo login..." -ForegroundColor Yellow
try {
    $loginBody = @{
        email = $adminEmail
        password = $adminPassword
    } | ConvertTo-Json
    
    $loginResponse = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body $loginBody `
        -ErrorAction SilentlyContinue
    
    $token = ($loginResponse.Content | ConvertFrom-Json).accessToken
    Write-Host "Token obtido com sucesso" -ForegroundColor Green
} catch {
    Write-Host "[ERRO] Falha ao fazer login: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Testar endpoint
Write-Host ""
Write-Host "Step 3: Testando endpoint /admin/metrics/health/$vendorId..." -ForegroundColor Yellow
$headers = @{
    "Authorization" = "Bearer $token"
}

try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/admin/metrics/health/$vendorId" `
        -Method GET `
        -Headers $headers `
        -ErrorAction Continue
    
    $statusCode = $response.StatusCode
    $body = $response.Content | ConvertFrom-Json
    
    if ($statusCode -eq 200) {
        Write-Host "Status: $statusCode" -ForegroundColor Green
        Write-Host ""
        Write-Host "Resposta:" -ForegroundColor Cyan
        Write-Host ($body | ConvertTo-Json) -ForegroundColor Green
        Write-Host ""
        Write-Host "[SUCESSO] Endpoint funcionando corretamente!" -ForegroundColor Green
    } else {
        Write-Host "Status: $statusCode" -ForegroundColor Red
    }
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode.Value)" -ForegroundColor Red
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
}
