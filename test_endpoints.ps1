#!/usr/bin/env powershell
# Script Rápido de Testes - LeadFlow Backend
# Uso: powershell.exe -ExecutionPolicy Bypass -File test_endpoints.ps1

param(
    [string]$Email = "carlos@leadflow.com",
    [string]$Password = "SenhaForte@123",
    [string]$Tenant = "public",
    [string]$Server = "http://localhost:8081"
)

Write-Host "╔════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║  LeadFlow Backend - Test Suite             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$headers = @{
    "X-Tenant-ID" = $Tenant
    "Content-Type" = "application/json"
}

# ============================================
# 1️⃣ TESTE DE HEALTH CHECK
# ============================================
Write-Host "1️⃣  Health Check..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod "$Server/actuator/health" -TimeoutSec 5
    if ($health.status -eq "UP") {
        Write-Host "   ✅ Servidor ativo" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️  Servidor respondendo: $($health.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   ❌ Servidor indisponível" -ForegroundColor Red
    exit 1
}

# ============================================
# 2️⃣ TESTE DE LOGIN
# ============================================
Write-Host "`n2️⃣  Login..." -ForegroundColor Yellow
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
    $refreshToken = $loginResponse.refreshToken
    
    Write-Host "   ✅ Login realizado" -ForegroundColor Green
    Write-Host "      📌 Email: $Email" -ForegroundColor Cyan
    Write-Host "      📌 Access Token: $($accessToken.Substring(0, 40))..." -ForegroundColor Cyan
    
} catch {
    Write-Host "   ❌ Erro ao fazer login" -ForegroundColor Red
    Write-Host "      Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    exit 1
}

# ============================================
# 3️⃣ TESTE DE REQUISIÇÃO AUTENTICADA
# ============================================
Write-Host "`n3️⃣  Requisição Autenticada (GET /api/leads)..." -ForegroundColor Yellow

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
    
    Write-Host "   ✅ Requisição autenticada bem-sucedida" -ForegroundColor Green
    
    if ($leadsResponse -is [System.Collections.ArrayList] -or $leadsResponse -is [Object[]]) {
        Write-Host "      📊 Total de leads: $($leadsResponse.Count)" -ForegroundColor Cyan
    } else {
        Write-Host "      📊 Resposta recebida com sucesso" -ForegroundColor Cyan
    }
    
} catch {
    if ($_.Exception.Response.StatusCode -eq "Unauthorized") {
        Write-Host "   ⚠️  Token inválido ou expirado" -ForegroundColor Yellow
    } else {
        Write-Host "   ❌ Erro na requisição autenticada" -ForegroundColor Red
        Write-Host "      Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
}

# ============================================
# 4️⃣ TESTE DE REGISTRO (NOVO USUÁRIO)
# ============================================
Write-Host "`n4️⃣  Registro de Novo Usuário..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$newEmail = "teste_$timestamp@leadflow.com"

$registerBody = @{
    name = "Usuário Teste $timestamp"
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
    
    Write-Host "   ✅ Usuário registrado" -ForegroundColor Green
    Write-Host "      📧 Email: $newEmail" -ForegroundColor Cyan
    
} catch {
    Write-Host "   ⚠️  Erro ao registrar" -ForegroundColor Yellow
    Write-Host "      Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
}

# ============================================
# RESUMO
# ============================================
Write-Host "`n╔════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  ✅ Testes Concluídos                      ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "📋 Resumo:" -ForegroundColor Cyan
Write-Host "   Server: $Server" -ForegroundColor Gray
Write-Host "   Tenant: $Tenant" -ForegroundColor Gray
Write-Host "   Email: $Email" -ForegroundColor Gray
Write-Host ""
