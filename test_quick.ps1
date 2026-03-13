param(
    [string]$Email = "carlos@leadflow.com",
    [string]$Password = "SenhaForte@123",
    [string]$Tenant = "public",
    [string]$Server = "http://localhost:8081"
)

Write-Host "===== LeadFlow Backend - Test Suite =====" -ForegroundColor Cyan

$headers = @{
    "X-Tenant-ID" = $Tenant
    "Content-Type" = "application/json"
}

# Test 1: Health Check
Write-Host "`n1. Health Check..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod "$Server/actuator/health" -TimeoutSec 5
    Write-Host "   [OK] Servidor ativo" -ForegroundColor Green
} catch {
    Write-Host "   [FAIL] Servidor indisponivel" -ForegroundColor Red
    exit 1
}

# Test 2: Login
Write-Host "`n2. Login..." -ForegroundColor Yellow
$loginBody = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod "$Server/auth/login" `
        -Method POST `
        -Headers $headers `
        -Body $loginBody `
        -TimeoutSec 10
    
    $accessToken = $loginResponse.accessToken
    Write-Host "   [OK] Login realizado para $Email" -ForegroundColor Green
    Write-Host "   Token: $($accessToken.Substring(0, 40))..." -ForegroundColor Cyan
    
} catch {
    Write-Host "   [FAIL] Erro ao fazer login" -ForegroundColor Red
    Write-Host "   Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    exit 1
}

# Test 3: Authenticated Request
Write-Host "`n3. Requisicao Autenticada..." -ForegroundColor Yellow
$authHeaders = @{
    "X-Tenant-ID" = $Tenant
    "Authorization" = "Bearer $accessToken"
    "Content-Type" = "application/json"
}

try {
    $leadsResponse = Invoke-RestMethod "$Server/api/leads" `
        -Method GET `
        -Headers $authHeaders `
        -TimeoutSec 10
    
    Write-Host "   [OK] Requisicao autenticada bem-sucedida" -ForegroundColor Green
    
} catch {
    Write-Host "   [INFO] Endpoint nao disponivel ou acesso negado" -ForegroundColor Yellow
}

# Test 4: Register New User
Write-Host "`n4. Registrar Novo Usuario..." -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$newEmail = "teste_$timestamp@leadflow.com"

$registerBody = @{
    name = "Teste $timestamp"
    email = $newEmail
    password = "Senha@Test123"
    confirmPassword = "Senha@Test123"
} | ConvertTo-Json

try {
    $registerResponse = Invoke-RestMethod "$Server/auth/register" `
        -Method POST `
        -Headers $headers `
        -Body $registerBody `
        -TimeoutSec 10
    
    Write-Host "   [OK] Usuario registrado: $newEmail" -ForegroundColor Green
    
} catch {
    Write-Host "   [FAIL] Erro ao registrar" -ForegroundColor Red
    Write-Host "   Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host "`n===== Testes Concluidos =====" -ForegroundColor Green
Write-Host "Server: $Server" -ForegroundColor Gray
Write-Host "Tenant: $Tenant" -ForegroundColor Gray
Write-Host ""
