#!/usr/bin/env pwsh

# Inicia o servidor em background capturando logs
Write-Host "Iniciando servidor com captura de logs..." -ForegroundColor Yellow

$logFile = "server-request-debug.log"
$serverProcess = Start-Process -FilePath "java" `
    -ArgumentList @(
        "-jar", 
        "target\leadflow-backend-1.0.0.jar"
    ) `
    -NoNewWindow `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $logFile `
    -PassThru

Write-Host "Servidor iniciando (PID: $($serverProcess.Id))" -ForegroundColor Cyan
Start-Sleep -Seconds 5

# Fazer a requisição de teste
Write-Host "`nCriando usuário teste..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "teste-$timestamp@leadflow.dev"

$token = $null
try {
    $r = Invoke-RestMethod -Uri "http://localhost:8081/auth/register" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id"  = "public"
        } `
        -Body (@{
            name = "Test"
            email = $testEmail
            password = "Pass123!@"
            confirmPassword = "Pass123!@"
        } | ConvertTo-Json)
    
    $token = $r.accessToken
    Write-Host "Usuário criado com sucesso" -ForegroundColor Green
}
catch {
    Write-Host "Erro ao criar usuário: $_" -ForegroundColor Red
    $serverProcess.Kill()
    exit 1
}

Write-Host "`nTestando GET /api/leads..." -ForegroundColor Yellow

try {
    $r = Invoke-RestMethod -Uri "http://localhost:8081/api/leads" `
        -Method GET `
        -Headers @{
            "X-Tenant-Id" = "public"
            "Authorization" = "Bearer $token"
        }
    Write-Host "✅ Sucesso" -ForegroundColor Green
}
catch {
    Write-Host "❌ Erro (observar log do servidor)" -ForegroundColor Red
}

Write-Host "`nAguardando 2 segundos para capturar logs..." -ForegroundColor Cyan
Start-Sleep -Seconds 2

# Parar o servidor
$serverProcess.Kill()
Write-Host "Servidor parado" -ForegroundColor Cyan

Write-Host "`nÚLTIMAAS 100 LINHAS DO LOG:" -ForegroundColor Yellow
Write-Host "════════════════════════════════════════" -ForegroundColor Cyan

Get-Content $logFile -Tail 100 | ForEach-Object {
    if ($_ -match "ERROR|Exception|Throwable|leads") {
        Write-Host $_ -ForegroundColor Red
    } else {
        Write-Host $_
    }
}
