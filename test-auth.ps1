param(
    [string]$Email = "user$(Get-Random -Minimum 10000 -Maximum 99999)@leadflow.com",
    [string]$Password = "Password@123"
)

$BaseUrl = "http://localhost:8081"
$Headers = @{"Content-Type"="application/json"; "X-Tenant-ID"="public"}

Write-Host "=========== TESTE DE AUTENTICACAO ===========" -ForegroundColor Yellow
Write-Host ""

# 1. REGISTER
Write-Host "[1/4] Registrando usuário..." -ForegroundColor Cyan
$registerBody = @{
    name = "Test User"
    email = $Email
    password = $Password
    confirmPassword = $Password
} | ConvertTo-Json

try {
    $registerResp = Invoke-RestMethod "$BaseUrl/auth/register" -Method POST -Headers $Headers -Body $registerBody
    Write-Host "✓ Usuário registrado" -ForegroundColor Green
    Write-Host "  Email: $Email"
    Write-Host "  Token: $($registerResp.accessToken.Substring(0,20))..."
    $token = $registerResp.accessToken
} catch {
    Write-Host "✗ Erro no registro: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 2. LOGIN
Write-Host "[2/4] Fazendo login..." -ForegroundColor Cyan
$loginBody = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

try {
    $loginResp = Invoke-RestMethod "$BaseUrl/auth/login" -Method POST -Headers $Headers -Body $loginBody
    Write-Host "✓ Login realizado" -ForegroundColor Green
    Write-Host "  Token: $($loginResp.accessToken.Substring(0,20))..."
    $authToken = $loginResp.accessToken
} catch {
    Write-Host "✗ Erro no login: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 3. GET /auth/me
Write-Host "[3/4] Buscando dados do usuário (/auth/me)..." -ForegroundColor Cyan
$authHeaders = @{"Authorization"="Bearer $authToken"; "X-Tenant-ID"="public"}

try {
    $meResp = Invoke-RestMethod "$BaseUrl/auth/me" -Method GET -Headers $authHeaders
    Write-Host "✓ Dados do usuário obtidos" -ForegroundColor Green
    Write-Host ($meResp | ConvertTo-Json)
} catch {
    Write-Host "✗ Erro em /auth/me: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""

# 4. GET /auth/sessions
Write-Host "[4/4] Listando sessões (/auth/sessions)..." -ForegroundColor Cyan

try {
    $sessionsResp = Invoke-RestMethod "$BaseUrl/auth/sessions" -Method GET -Headers $authHeaders
    Write-Host "✓ Sessões obtidas" -ForegroundColor Green
    Write-Host "  Total de sessões: $(($sessionsResp | Measure-Object).Count)"
    Write-Host ($sessionsResp | ConvertTo-Json -Depth 5)
} catch {
    Write-Host "✗ Erro em /auth/sessions: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=========== TESTES COMPLETOS ===========" -ForegroundColor Green
Write-Host "TODOS OS ENDPOINTS FUNCIONANDO!" -ForegroundColor Green
