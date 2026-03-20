#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "LEADFLOW - DEBUG LEADS ENDPOINT ERROR" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

# Create test user
Write-Host "1️⃣  Criar usuário teste..." -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "teste-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/auth/register" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id"  = $TenantHeader
        } `
        -Body (@{
            name = "Test User"
            email = $testEmail
            password = $testPassword
            confirmPassword = $testPassword
        } | ConvertTo-Json)
    
    Write-Host "✅ Usuário criado: $testEmail" -ForegroundColor Green
    $token = $r.accessToken
    Write-Host "Token: $($token.Substring(0, 20))..." -ForegroundColor Cyan
}
catch {
    Write-Host "❌ Erro ao criar usuário: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Get leads with error details
Write-Host "`n2️⃣  Testar GET /api/leads..." -ForegroundColor Yellow

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/leads" `
        -Method GET `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id"  = $TenantHeader
            "Authorization" = "Bearer $token"
        }
    Write-Host "✅ Sucesso!" -ForegroundColor Green
    $r | ConvertTo-Json | Write-Host
}
catch {
    Write-Host "❌ HTTP $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    
    # Try to read response body
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "`nResponse Body:" -ForegroundColor Yellow
        Write-Host $body
        
        # Try to parse as JSON
        try {
            $json = $body | ConvertFrom-Json
            Write-Host "`nError Details:" -ForegroundColor Cyan
            $json | ConvertTo-Json | Write-Host
        } catch {}
    } catch {}
}

# Try with admin header to see if that helps
Write-Host "`n3️⃣  Testar POST /api/leads (criar novo)..." -ForegroundColor Yellow

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/leads" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id"  = $TenantHeader
            "Authorization" = "Bearer $token"
        } `
        -Body (@{
            name = "Test Lead"
            email = "lead@test.com"
            phone = "+5511"
        } | ConvertTo-Json)
    
    Write-Host "✅ Sucesso!" -ForegroundColor Green
    $r | ConvertTo-Json | Write-Host
}
catch {
    Write-Host "❌ HTTP $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "`nResponse Body:" -ForegroundColor Yellow
        Write-Host $body
        
        try {
            $json = $body | ConvertFrom-Json
            Write-Host "`nError Details:" -ForegroundColor Cyan
            $json | ConvertTo-Json | Write-Host
        } catch {}
    } catch {}
}

Write-Host "`n════════════════════════════════════════════════════════`n" -ForegroundColor Cyan
