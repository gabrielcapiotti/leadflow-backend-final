#!/usr/bin/env powershell
# Teste simples para ver se conseguimos fazer login ou criar um usuário regular primeiro

$BaseUrl = "http://localhost:8081"
$Email = "testadmin@leadflow.test"
$Password = "TestAdmin@123"

Write-Host "Tentando registrar usuário regular primeiro..." -ForegroundColor Cyan

$body = @{
    name = "Test Admin"
    email = $Email
    password = $Password
} | ConvertTo-Json

Write-Host "Request:" -ForegroundColor Yellow
Write-Host $body
Write-Host ""

try {
    $response = Invoke-WebRequest -Method POST `
        -Uri "$BaseUrl/api/auth/register" `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body $body `
        -ErrorAction SilentlyContinue `
        -WarningAction SilentlyContinue
    
    Write-Host "✅ SUCCESS (HTTP $($response.StatusCode))" -ForegroundColor Green
    $data = $response.Content | ConvertFrom-Json
    Write-Host "Token: $($data.accessToken.Substring(0, 50))..."
    Write-Host "Tenant: $($data.tenantId)"
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED (HTTP $statusCode)" -ForegroundColor Red
}
