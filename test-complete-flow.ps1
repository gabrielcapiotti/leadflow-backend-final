# ===== TEST COMPLETE AUTH FLOW =====
$BaseUrl = "http://localhost:8081"
$Email = "user$(Get-Random -Min 10000 -Max 99999)@test.com"
$Password = "Password@123"

Write-Host ""
Write-Host "====== TESTE COMPLETO DE AUTENTICACAO ======" -ForegroundColor Cyan
Write-Host ""

# 1. REGISTER
Write-Host "[1/4] POST /auth/register" -ForegroundColor Yellow
try {
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
    Write-Host "  Token: $($resp.accessToken.Substring(0,30))..." -ForegroundColor Gray
    $Token = $resp.accessToken
} catch {
    Write-Host "✗ ERRO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 2. LOGIN
Write-Host "[2/4] POST /auth/login" -ForegroundColor Yellow
try {
    $body = @{
        email = $Email
        password = $Password
    } | ConvertTo-Json
    
    $resp = Invoke-RestMethod "$BaseUrl/auth/login" -Method POST `
        -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} `
        -Body $body
    
    Write-Host "✓ Login realizado" -ForegroundColor Green
    Write-Host "  Token: $($resp.accessToken.Substring(0,30))..." -ForegroundColor Gray
    $Token = $resp.accessToken
} catch {
    Write-Host "✗ ERRO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. GET /auth/me
Write-Host "[3/4] GET /auth/me" -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod "$BaseUrl/auth/me" -Method GET `
        -Headers @{"Authorization"="Bearer $Token";"X-Tenant-ID"="public"}
    
    Write-Host "✓ Dados do usuario:" -ForegroundColor Green
    Write-Host "  ID: $($resp.id)" -ForegroundColor Gray
    Write-Host "  Email: $($resp.email)" -ForegroundColor Gray
    Write-Host "  Role: $($resp.role)" -ForegroundColor Gray
} catch {
    Write-Host "✗ ERRO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 4. GET /auth/sessions
Write-Host "[4/4] GET /auth/sessions" -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod "$BaseUrl/auth/sessions" -Method GET `
        -Headers @{"Authorization"="Bearer $Token";"X-Tenant-ID"="public"}
    
    Write-Host "✓ Sessoes listadas:" -ForegroundColor Green
    Write-Host "  Total: $(($resp | Measure-Object).Count) sessao(oes)" -ForegroundColor Gray
    
    if ($resp.Count -gt 0) {
        foreach ($session in $resp) {
            Write-Host "  - ID: $($session.sessionId)" -ForegroundColor Gray
            Write-Host "    IP: $($session.ipAddress)" -ForegroundColor Gray
            Write-Host "    Current: $($session.current)" -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "✗ ERRO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "====== TESTE CONCLUIDO COM SUCESSO! ======" -ForegroundColor Green
Write-Host ""
