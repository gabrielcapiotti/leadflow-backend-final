Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO ENDPOINTS DO ADMIN" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081"
$adminEmail = "admin.tester@leadflow.com"
$adminPassword = "AdminTest@123"

# Tentar registrar novo admin (ou usar se já existe)
Write-Host "📝 Registrando novo admin..." -ForegroundColor Yellow
$regBody = @{
    name = "Admin Tester"
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

try {
    $regResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $regBody `
        -ErrorAction Stop
    Write-Host "✅ Admin criado" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "⚠️  Status $status - pode já existir" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "🔑 Realizando login..." -ForegroundColor Yellow

$loginBody = @{
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

$token = $null
try {
    $loginResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $loginBody `
        -ErrorAction Stop
    
    $token = ($loginResponse.Content | ConvertFrom-Json).accessToken
    Write-Host "✅ Login realizado com sucesso" -ForegroundColor Green
    
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Erro de Login: $status" -ForegroundColor Red
    exit 1
}

if ($null -eq $token) {
    Write-Host "❌ Token não obtido" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Testando 5 endpoints admin:" -ForegroundColor Yellow
Write-Host ""

# 1. GET /admin/overview
Write-Host "1️⃣  GET /admin/overview" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/overview" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   ✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 2. GET /admin/metrics/growth?days=30
Write-Host "2️⃣  GET /admin/metrics/growth?days=30" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/growth?days=30" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   ✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 3. GET /admin/metrics/cohorts
Write-Host "3️⃣  GET /admin/metrics/cohorts" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/cohorts" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   ✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 4. GET /admin/metrics/forecast?months=6
Write-Host "4️⃣  GET /admin/metrics/forecast?months=6" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/forecast?months=6" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   ✅ Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 5. GET /admin/metrics/health/{vendorId}
Write-Host "5️⃣  GET /admin/metrics/health/{vendorId}" -ForegroundColor Cyan
Write-Host "   (Buscando um vendor ID no banco...)" -ForegroundColor Gray

# Buscar um vendor ID
$env:PGPASSWORD = "venusia"
$vendorOutput = psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT id FROM public.vendors LIMIT 1;" 2>&1
$vendorId = $vendorOutput.Trim()

if ($vendorId -and $vendorId -ne "") {
    Write-Host "   Vendor ID: $vendorId" -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/health/$vendorId" `
            -Method GET `
            -Headers @{"Authorization" = "Bearer $token"} `
            -ContentType "application/json" `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Host "   ✅ Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "   Response:" -ForegroundColor White
        $data | ConvertTo-Json | Write-Host
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        Write-Host "   ❌ Status: $status" -ForegroundColor Red
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            $reader.Close()
            Write-Host "   Error: $body" -ForegroundColor Yellow
        } catch {}
    }
} else {
    Write-Host "   ⚠️  Nenhum vendor encontrado no banco" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTES COMPLETOS!" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Cyan
