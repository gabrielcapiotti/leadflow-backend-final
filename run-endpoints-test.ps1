#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "LEADFLOW - TESTE COMPLETO DE ENDPOINTS" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Server: $BaseUrl"
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

# Helper function
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body = $null,
        [bool]$RequireAuth = $false,
        [string]$Token = $null,
        [string]$TenantId = $null
    )

    $Headers = @{
        "Content-Type" = "application/json"
    }

    # Add tenant header if provided or use default
    if ($TenantId) {
        $Headers["X-Tenant-Id"] = $TenantId
    } elseif ($TenantHeader) {
        $Headers["X-Tenant-Id"] = $TenantHeader
    }

    if ($RequireAuth -and $Token) {
        $Headers["Authorization"] = "Bearer $Token"
    }

    try {
        $params = @{
            Uri     = "$BaseUrl$Endpoint"
            Method  = $Method
            Headers = $Headers
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
        }

        $response = Invoke-RestMethod @params
        return @{ Success = $true; Status = 200; Data = $response; Headers = $Headers }
    }
    catch {
        $status = 0
        try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
        return @{ Success = $false; Status = $status; Exception = $_.Exception.Message; Headers = $Headers }
    }
}

# Create test user
Write-Host "STEP 1: Criar usuário teste" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "teste-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Test User"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if ($r.Success) {
    Write-Host "✅ Usuário criado: $testEmail" -ForegroundColor Green
    $token = $r.Data.accessToken
    
    # Try to extract vendor ID from response
    if ($r.Data.user -and $r.Data.user.vendorId) {
        $VendorTenant = $r.Data.user.vendorId
        Write-Host "✅ Vendor ID extraído: $VendorTenant" -ForegroundColor Green
    } elseif ($r.Data.vendorId) {
        $VendorTenant = $r.Data.vendorId
        Write-Host "✅ Vendor ID extraído: $VendorTenant" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Vendor ID não encontrado na resposta, usando 'public'" -ForegroundColor Yellow
        $VendorTenant = "public"
    }
} else {
    Write-Host "❌ Erro ao criar usuário: $($r.Exception)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test endpoints
Write-Host "STEP 2: Testar endpoints" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════"
Write-Host "Usando Tenant-ID: $VendorTenant" -ForegroundColor Gray
Write-Host ""

# Create vendor for the user (auto-created, may return 409)
Write-Host "[INFO] Tentando criar vendor (pode retornar 409 - esperado)" -ForegroundColor Gray

$r = Invoke-ApiRequest "POST" "/api/vendors" @{
    name = "Test Vendor"
    userEmail = $testEmail
    active = $true
} $true $token $VendorTenant

if ($r.Success) {
    Write-Host "✅ Vendor criado" -ForegroundColor Green
} else {
    Write-Host "⚠️  Status $($r.Status) - $($r.Exception)" -ForegroundColor Yellow
}
Write-Host ""

# Test endpoints
Write-Host "STEP 3: Testar endpoints" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════"

$endpoints = @(
    @{Name="GET /api/leads"; Method="GET"; Endpoint="/api/leads"},
    @{Name="POST /api/leads"; Method="POST"; Endpoint="/api/leads"; Body=@{name="Test Lead"; email="lead@test.com"; phone="+5511"}},
    @{Name="GET /api/vendor-leads/metrics"; Method="GET"; Endpoint="/api/vendor-leads/metrics"}
)

$passed = 0
$failed = 0

foreach ($ep in $endpoints) {
    $r = Invoke-ApiRequest $ep.Method $ep.Endpoint $(if ($ep.Body) { $ep.Body } else { $null }) $true $token $VendorTenant
    
    if ($r.Success) {
        Write-Host "✅ $($ep.Name) (HTTP 200)" -ForegroundColor Green
        Write-Host "   [Header] X-Tenant-Id: $($r.Headers.'X-Tenant-Id')" -ForegroundColor Gray
        $passed++
    } else {
        Write-Host "❌ $($ep.Name) (HTTP $($r.Status))" -ForegroundColor Red
        Write-Host "   [Header] X-Tenant-Id: $($r.Headers.'X-Tenant-Id')" -ForegroundColor Gray
        Write-Host "   [Error] $($r.Exception)" -ForegroundColor DarkRed
        $failed++
    }
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════"
Write-Host "RESUMO" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════"
Write-Host "Testes: $($passed + $failed)"
Write-Host "✅ Passou: $passed" -ForegroundColor Green
Write-Host "❌ Falhou: $failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "Taxa: $([math]::Round(($passed / ($passed + $failed)) * 100))%"
Write-Host ""
