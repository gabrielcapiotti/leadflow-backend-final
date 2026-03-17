Write-Host "Investigando se Setting foi criada no banco..." -ForegroundColor Cyan
Write-Host ""

$loginBody = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json
$resp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$data = $resp.Content | ConvertFrom-Json
$token = $data.accessToken

$parts = $token.Split('.')
$payload = $parts[1]
while ($payload.Length % 4) { $payload += "=" }
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
$tokenData = $decoded | ConvertFrom-Json
$userId = $tokenData.userId

Write-Host "User ID: $userId" -ForegroundColor Green
Write-Host "Tenant: $($tokenData.tenant)" -ForegroundColor Yellow
Write-Host ""

$h = @{"Authorization"="Bearer $token";"X-Tenant-ID"="public";"Content-Type"="application/json"}

# Passo 1: Criar Setting via PUT /api/me/settings
Write-Host "PASO 1: Criando Setting via PUT /api/me/settings" -ForegroundColor Yellow
$body = @{ vendorName="Test"; whatsapp="11999999999"; companyName="TestCo" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $h -Body $body -UseBasicParsing
$settingData = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
Write-Host "Setting ID retornado: $($settingData.id)" -ForegroundColor Cyan
Write-Host "Setting ID type: $($settingData.id.GetType().Name)" -ForegroundColor Cyan
$settingId = $settingData.id
Write-Host ""

# Passo 2: GET via /api/me/settings
Write-Host "PASO 2: Recuperando via GET /api/me/settings" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method GET -Headers $h -UseBasicParsing
$settingData2 = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
Write-Host "Setting ID no GET: $($settingData2.id)" -ForegroundColor Cyan
Write-Host ""

# Passo 3: Tentar GET via /api/settings/{id}
Write-Host "PASO 3: Tentando GET /api/settings/$settingId (ADMIN)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" -Method GET -Headers $h -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
    $settingData3 = $r.Content | ConvertFrom-Json
    Write-Host "Success! Setting found." -ForegroundColor Green
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Message: Setting not found in database" -ForegroundColor Red
}
Write-Host ""

# Passo 4: Tentar GET via /public/settings/{id}
Write-Host "PASO 4: Tentando GET /public/settings/$settingId (PUBLIC)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/public/settings/$settingId" -Method GET -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
    Write-Host "Success! Public endpoint works!" -ForegroundColor Green
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}
