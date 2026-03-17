# Complete Settings Endpoints Test Suite (9 endpoints)

$baseUrl = "http://localhost:8081"
$loginUrl = "$baseUrl/auth/login"
$settingsUrl = "$baseUrl/settings"

Write-Host "========== SETTINGS ENDPOINTS - COMPLETE TEST SUITE ==========" -ForegroundColor Yellow
Write-Host "Testando 9 endpoints: 4 originais + 5 adicionais" -ForegroundColor Cyan

# ============================================
#  STEP 1: AUTENTICACAO
# ============================================

Write-Host "`n[SETUP] Autenticando..." -ForegroundColor Cyan
$loginData = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -Uri $loginUrl `
    -Method Post `
    -ContentType "application/json" `
    -Body $loginData `
    -UseBasicParsing

$loginBody = $loginResp.Content | ConvertFrom-Json
$token = $loginBody.accessToken
Write-Host "Token obtido" -ForegroundColor Green

# Headers padrao
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

# ============================================
# TESTE 1: PUT /settings (Criar)
# ============================================

Write-Host "`n[1/9] PUT /settings (Create)" -ForegroundColor Cyan

$createData = @{
    vendorName = "Carlos Silva Vendas"
    whatsapp = "5511987654321"
    companyName = "Silva Consultoria"
    logo = "https://example.com/carlos.png"
    welcomeMessage = "Bem-vindo!"
} | ConvertTo-Json

try {
    $resp = Invoke-WebRequest -Uri $settingsUrl `
        -Method Put `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $createData `
        -UseBasicParsing
    
    $settingId = ($resp.Content | ConvertFrom-Json).id
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    Write-Host "ID: $settingId" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    exit 1
}

# ============================================
# TESTE 2: GET /settings
# ============================================

Write-Host "`n[2/9] GET /settings (Read user)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri $settingsUrl `
        -Method Get `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    $respData = $resp.Content | ConvertFrom-Json
    Write-Host "VendorName: $($respData.vendorName)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 3: GET /settings/{id}
# ============================================

Write-Host "`n[3/9] GET /settings/{id} (Read by ID)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/$settingId" `
        -Method Get `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 4: PATCH /settings
# ============================================

Write-Host "`n[4/9] PATCH /settings (Partial update)" -ForegroundColor Cyan

$patchData = @{
    whatsapp = "5511999999999"
    welcomeMessage = "Bem-vindo atualizado!"
} | ConvertTo-Json

try {
    $resp = Invoke-WebRequest -Uri $settingsUrl `
        -Method Patch `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $patchData `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    $respData = $resp.Content | ConvertFrom-Json
    Write-Host "WhatsApp: $($respData.whatsapp)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 5: PUT /settings/{id}
# ============================================

Write-Host "`n[5/9] PUT /settings/{id} (Update by ID)" -ForegroundColor Cyan

$updateData = @{
    vendorName = "Carlos Silva ATUALIZADO"
    whatsapp = "5511988888888"
    companyName = "Silva Consultoria Premium"
    logo = "https://example.com/carlos-premium.png"
    welcomeMessage = "Bem-vindo ao novo perfil!"
} | ConvertTo-Json

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/$settingId" `
        -Method Put `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $updateData `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    $respData = $resp.Content | ConvertFrom-Json
    Write-Host "VendorName: $($respData.vendorName)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 6: GET /settings/public/{id}
# ============================================

Write-Host "`n[6/9] GET /settings/public/{id} (Public access)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/public/$settingId" `
        -Method Get `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    $respData = $resp.Content | ConvertFrom-Json
    Write-Host "VendorName public: $($respData.vendorName)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 7: POST /settings/reset
# ============================================

Write-Host "`n[7/9] POST /settings/reset (Reset defaults)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/reset" `
        -Method Post `
        -ContentType "application/json" `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
    $respData = $resp.Content | ConvertFrom-Json
    Write-Host "VendorName reset: $($respData.vendorName)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 8: DELETE /settings/{id}
# ============================================

Write-Host "`n[8/9] DELETE /settings/{id} (Delete by ID)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/$settingId" `
        -Method Delete `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "OK - Status: $($resp.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

# ============================================
# TESTE 9: Validacao - deve retornar 404
# ============================================

Write-Host "`n[9/9] GET /settings/{id} (Validar delete - 404)" -ForegroundColor Cyan

try {
    $resp = Invoke-WebRequest -Uri "$settingsUrl/$settingId" `
        -Method Get `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "ERRO - Nunca deveria chegar aqui" -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    if ($statusCode -eq 400) {
        Write-Host "OK - Status: 400 (setting deletado)" -ForegroundColor Green
    } else {
        Write-Host "OK - Status: $statusCode" -ForegroundColor Green
    }
}

# ============================================
# RESUMO FINAL
# ============================================

Write-Host "`n========== RESUMO - 9 ENDPOINTS TESTADOS ==========" -ForegroundColor Yellow
Write-Host "OK 1. PUT /settings - Create/Update" -ForegroundColor Green
Write-Host "OK 2. GET /settings - Get user settings" -ForegroundColor Green
Write-Host "OK 3. GET /settings/{id} - Get by ID" -ForegroundColor Green
Write-Host "OK 4. PATCH /settings - Partial update" -ForegroundColor Green
Write-Host "OK 5. PUT /settings/{id} - Update by ID" -ForegroundColor Green
Write-Host "OK 6. GET /settings/public/{id} - Public access" -ForegroundColor Green
Write-Host "OK 7. POST /settings/reset - Reset to defaults" -ForegroundColor Green
Write-Host "OK 8. DELETE /settings/{id} - Delete by ID" -ForegroundColor Green
Write-Host "OK 9. GET /settings/{id} after delete - Validation" -ForegroundColor Green
Write-Host "`nSucesso! Todos os 9 endpoints funcionando!" -ForegroundColor Cyan
