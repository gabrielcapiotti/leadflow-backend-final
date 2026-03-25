# Test Vendor CRUD operations

$BaseURL = "http://localhost:8081"
$u = "vendor_$(Get-Date -Format 'yyyyMMddHHmmss')@leadflow.dev"
$p = "SecurePassword123!"

Write-Host "================================" -ForegroundColor Cyan
Write-Host "TESTE VENDOR CRUD" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Register
Write-Host "Step 1: Registrar usuário $u" -ForegroundColor Yellow
try {
    $r1 = Invoke-WebRequest -Uri "$BaseURL/auth/register" -Method POST `
        -Headers @{"X-Tenant-Id"="public";"Content-Type"="application/json"} `
        -Body (@{email=$u;password=$p;confirmPassword=$p;name="Vendor Tester"} | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ Registro OK" -ForegroundColor Green
} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 2: Login
Write-Host "Step 2: Login" -ForegroundColor Yellow
try {
    $r2 = Invoke-WebRequest -Uri "$BaseURL/auth/login" -Method POST `
        -Headers @{"X-Tenant-Id"="public";"Content-Type"="application/json"} `
        -Body (@{email=$u;password=$p} | ConvertTo-Json) `
        -UseBasicParsing -ErrorAction Stop
    $lr = $r2.Content | ConvertFrom-Json
    $tok = $lr.accessToken
    Write-Host "✅ Login OK" -ForegroundColor Green
} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Get Tenant ID
Write-Host "Step 3: Obter Tenant ID" -ForegroundColor Yellow
try {
    $r3 = Invoke-WebRequest -Uri "$BaseURL/auth/me" -Method GET `
        -Headers @{"X-Tenant-Id"="public";"Authorization"="Bearer $tok"} `
        -UseBasicParsing -ErrorAction Stop
    $pr = $r3.Content | ConvertFrom-Json
    $tid = $pr.tenantId
    Write-Host "✅ Tenant: $tid" -ForegroundColor Green
} catch {
    Write-Host "❌ Erro: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: Create Vendor
Write-Host "Step 4: Criar Vendor" -ForegroundColor Yellow
$slug = "vendor-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
$vendorBody = @{
    name = "Test Vendor"
    nomeVendedor = "Gabriel"
    userEmail = $u
    nomeEmpresa = "Tech Corp"
    whatsappVendedor = "+5511987654321"
    slug = $slug
} | ConvertTo-Json

try {
    $r4 = Invoke-WebRequest -Uri "$BaseURL/vendors" -Method POST `
        -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"=$tid;"Content-Type"="application/json"} `
        -Body $vendorBody `
        -UseBasicParsing -ErrorAction Stop
    $vdata = $r4.Content | ConvertFrom-Json
    $vid = $vdata.id
    Write-Host "✅ Vendor criado com ID: $vid" -ForegroundColor Green
} catch {
    Write-Host "❌ Erro: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        $reader.Dispose()
        Write-Host "Detalhes: $body" -ForegroundColor DarkRed
    } catch { }
    exit 1
}

# Test 6: Get Vendor
Write-Host ""
Write-Host "TEST 6: Get Vendor by ID" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "$BaseURL/vendors/$vid" -Method GET `
        -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"=$tid} `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ GET Success (HTTP $($r.StatusCode))" -ForegroundColor Green
    $data = $r.Content | ConvertFrom-Json
    Write-Host "   Name: $($data.name)" -ForegroundColor Gray
} catch {
    Write-Host "❌ GET Failed: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
}

# Test 13: Delete Vendor
Write-Host ""
Write-Host "TEST 13: Delete Vendor" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "$BaseURL/vendors/$vid" -Method DELETE `
        -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"=$tid} `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ DELETE Success (HTTP $($r.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "❌ DELETE Failed: $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
}

# Test 14: Verify Deletion
Write-Host ""
Write-Host "TEST 14: Verify Deletion" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "$BaseURL/vendors/$vid" -Method GET `
        -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"=$tid} `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "⚠️  Got HTTP $($r.StatusCode) - vendor still exists?" -ForegroundColor Yellow
} catch {
    $code = $_.Exception.Response.StatusCode.Value__
    if ($code -eq 404) {
        Write-Host "✅ Got 404 - Vendor correctly deleted" -ForegroundColor Green
    } else {
        Write-Host "❌ Got HTTP $code instead of 404" -ForegroundColor Red
    }
}
