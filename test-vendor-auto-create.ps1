#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "LEADFLOW - TEST AUTO-VENDOR CREATION" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

# Helper function
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body = $null,
        [bool]$RequireAuth = $false,
        [string]$Token = $null
    )

    $Headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantHeader
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
        return @{ Success = $true; Status = 200; Data = $response }
    }
    catch {
        $status = 0
        try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
        return @{ Success = $false; Status = $status; Exception = $_.Exception.Message }
    }
}

# 1. Registrar novo usuário
Write-Host "FASE 1: Registrar Novo Usuário" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════`n"

$timestamp = Get-Date -Format "yyyyMMddHHmmssffff"
$testEmail = "auto-vendor-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

Write-Host "Email: $testEmail"
$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Auto Vendor Test"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if (-not $r.Success) {
    Write-Host "❌ Erro ao registrar: HTTP $($r.Status)" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Usuário registrado com sucesso" -ForegroundColor Green
$token = $r.Data.accessToken
Write-Host "Token: $($token.Substring(0, 30))...`n" -ForegroundColor Cyan

# 2. Testar endpoints que precisam de vendor
Write-Host "FASE 2: Testar Endpoints de Leads" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════`n"

$tests = @(
    @{Name="GET /api/leads"; Method="GET"; Endpoint="/api/leads"; NeedAuth=$true},
    @{Name="POST /api/leads"; Method="POST"; Endpoint="/api/leads"; Body=@{name="Lead 1"; email="lead@example.com"; phone="+5511"}; NeedAuth=$true},
    @{Name="GET /api/vendor-leads/metrics"; Method="GET"; Endpoint="/api/vendor-leads/metrics"; NeedAuth=$true}
)

$passed = 0
$failed = 0

foreach ($test in $tests) {
    $r = Invoke-ApiRequest $test.Method $test.Endpoint $(if ($test.Body) { $test.Body } else { $null }) $test.NeedAuth $token
    
    if ($r.Success) {
        Write-Host "✅ $($test.Name) (HTTP 200)" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "❌ $($test.Name) (HTTP $($r.Status)): $($r.Exception.Substring(0, 60))..." -ForegroundColor Red
        $failed++
    }
}

Write-Host "`n════════════════════════════════════════════════════════"
Write-Host "RESULTADO FINAL" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════"
Write-Host "Testes: $($passed + $failed)"
Write-Host "✅ Passou: $passed" -ForegroundColor Green
Write-Host "❌ Falhou: $failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "Taxa de Sucesso: $([math]::Round(($passed / ($passed + $failed)) * 100))%"

if ($passed -eq 3) {
    Write-Host "`n🎉 TODOS OS TESTES PASSARAM!" -ForegroundColor Yellow
} else {
    Write-Host "`n⚠️  Alguns testes falharam - vendor auto-creation pode não estar funcionando" -ForegroundColor Yellow
}

Write-Host ""
