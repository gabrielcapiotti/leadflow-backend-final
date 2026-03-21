#!/usr/bin/env pwsh

$payload = '{
  "email": "admin@leadflow.com",
  "password": "Admin@Lead123"
}'

Write-Host "Admin Login Test" -ForegroundColor Cyan
Write-Host "Payload: $payload" -ForegroundColor Cyan
Write-Host ""
Write-Host "Sending login request..." -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri 'http://localhost:8081/auth/login' `
        -Method POST `
        -ContentType 'application/json' `
        -Body $payload `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Host "✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Cyan
    Write-Host ($response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10)
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Error Response: $body" -ForegroundColor Red
    }
    catch {
        Write-Host "Error: $_" -ForegroundColor Red
    }
}
Write-Host "Password: Admin@123456" -ForegroundColor Yellow
Write-Host ""

$headers = @{"Content-Type" = "application/json"}

try {
    Write-Host "⏳ Enviando solicitação de login..." -ForegroundColor Cyan
    $response = Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body -TimeoutSec 30
    
    Write-Host "✅ LOGIN SUCCESSFUL!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Response:" -ForegroundColor Cyan
    $response.Content | ConvertFrom-Json | ConvertTo-Json | Write-Host
    
    $token = ($response.Content | ConvertFrom-Json).accessToken
    Write-Host ""
    Write-Host "Access Token: $($token.Substring(0, 50))..." -ForegroundColor Green
    
} catch {
    $ex = $_.Exception
    if ($ex.Response) {
        $status = $ex.Response.StatusCode.Value__
        Write-Host "❌ LOGIN FAILED - HTTP $status" -ForegroundColor Red
        Write-Host ""
        $streamReader = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
        $body = $streamReader.ReadToEnd()
        $streamReader.Close()
        Write-Host "Response Body:" -ForegroundColor Yellow
        Write-Host $body -ForegroundColor Yellow
    } else {
        Write-Host "❌ Erro de conexão: $($ex.Message)" -ForegroundColor Red
    }
}
