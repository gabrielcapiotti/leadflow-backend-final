# ===== TEST COMPLETE AUTH FLOW WITH DETAILED SESSION OUTPUT =====
$BaseUrl = "http://localhost:8081"
$Email = "user$(Get-Random -Min 10000 -Max 99999)@test.com"
$Password = "Password@123"

Write-Host ""
Write-Host "====== TESTE COMPLETO DE AUTENTICACAO ======" -ForegroundColor Cyan
Write-Host ""

# 1. REGISTER
Write-Host "[1/4] POST /auth/register" -ForegroundColor Yellow
$body = @{
    name = "Test User"
    email = $Email
    password = $Password
    confirmPassword = $Password
} | ConvertTo-Json

$resp = Invoke-RestMethod "$BaseUrl/auth/register" -Method POST `
    -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} `
    -Body $body

Write-Host "✓ Usuario registrado: $Email" -ForegroundColor Green
$Token = $resp.accessToken

Write-Host ""

# 2. LOGIN
Write-Host "[2/4] POST /auth/login" -ForegroundColor Yellow
$body = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

$resp = Invoke-RestMethod "$BaseUrl/auth/login" -Method POST `
    -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} `
    -Body $body

Write-Host "✓ Login realizado" -ForegroundColor Green
$Token = $resp.accessToken

Write-Host ""

# 3. GET /auth/me
Write-Host "[3/4] GET /auth/me" -ForegroundColor Yellow
$resp = Invoke-RestMethod "$BaseUrl/auth/me" -Method GET `
    -Headers @{"Authorization"="Bearer $Token";"X-Tenant-ID"="public"}

Write-Host "✓ Dados do usuario:" -ForegroundColor Green
Write-Host ($resp | ConvertTo-Json -Depth 5) -ForegroundColor Gray

Write-Host ""

# 4. GET /auth/sessions - DETAILED VIEW
Write-Host "[4/4] GET /auth/sessions" -ForegroundColor Yellow
$sessions = Invoke-RestMethod "$BaseUrl/auth/sessions" -Method GET `
    -Headers @{"Authorization"="Bearer $Token";"X-Tenant-ID"="public"}

Write-Host "✓ Sessoes listadas:" -ForegroundColor Green
Write-Host ""

if ($sessions -is [array]) {
    Write-Host "Total de sessoes: $($sessions.Count)" -ForegroundColor Cyan
} else {
    Write-Host "Total de sessoes: 1" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "Detalhes da sessao:" -ForegroundColor Yellow
$sessions | ConvertTo-Json -Depth 10

Write-Host ""
Write-Host "====== RESPOSTA FORMATADA ======" -ForegroundColor Cyan
$sessions | Format-Table -AutoSize

Write-Host ""
Write-Host "====== TESTE CONCLUIDO COM SUCESSO! ======" -ForegroundColor Green
Write-Host ""
