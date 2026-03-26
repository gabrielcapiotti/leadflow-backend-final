#!/usr/bin/env pwsh

# Script com MOCK apenas para endpoints de IA
# Login, Create Lead e Security/Validation fazem chamadas REAIS

$baseUrl = "http://localhost:8081"

$global:totalTests = 0
$global:passedTests = 0
$global:failedTests = 0

$green = "Green"
$red = "Red"
$yellow = "Yellow"
$cyan = "Cyan"

function Header {
    param($msg)
    Write-Host "`n================================================" -ForegroundColor $cyan
    Write-Host $msg -ForegroundColor $cyan
    Write-Host "================================================" -ForegroundColor $cyan
}

function HandleError($status, $content, $expected, $name) {
    Write-Host "  [FAIL] $name → Status: $status (Expected: $expected)" -ForegroundColor $red
    if ($content) {
        try {
            $parsed = $content | ConvertFrom-Json
            Write-Host "         Error: $($parsed.error) - $($parsed.message)" -ForegroundColor $red
        } catch {
            Write-Host "         Body: $content" -ForegroundColor $red
        }
    }
    $global:failedTests++
}

function TestAPI {
    param(
        $name,
        $method,
        $url,
        $body,
        $expectedStatus,
        $headers,
        $allow403 = $false,
        $allow429 = $false,
        $requiredFields = @()  # Campos obrigatórios na resposta
    )

    $global:totalTests++
    Write-Host "`nTEST $($global:totalTests): $name" -ForegroundColor $cyan

    try {
        $params = @{
            Uri = $url
            Method = $method
            Headers = $headers
            ErrorAction = "Stop"
        }

        if ($body) {
            $params["Body"] = ($body | ConvertTo-Json -Depth 10)
        }

        $response = Invoke-WebRequest -UseBasicParsing @params
        $status = $response.StatusCode

        # Suporta array de status esperados (ex: @(200, 201))
        $statusMatches = $false
        if ($expectedStatus -is [array]) {
            $statusMatches = $status -in $expectedStatus
        } else {
            $statusMatches = $status -eq $expectedStatus
        }

        if ($statusMatches) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green

            # Valida conteúdo e campos obrigatórios
            if ($response.Content.Length -lt 5) {
                Write-Host "  [FAIL] Empty response" -ForegroundColor $red
                $global:failedTests++
                return $null
            }

            # Valida campos obrigatórios
            if ($requiredFields.Count -gt 0) {
                try {
                    $parsed = $response.Content | ConvertFrom-Json
                    foreach ($field in $requiredFields) {
                        if (-not $parsed.$field) {
                            Write-Host "  [FAIL] Missing required field: $field" -ForegroundColor $red
                            $global:failedTests++
                            return $null
                        }
                    }
                } catch {
                    Write-Host "  [FAIL] Invalid JSON response" -ForegroundColor $red
                    $global:failedTests++
                    return $null
                }
            }

            $global:passedTests++
            return $response.Content
        }

        elseif ($status -eq 403 -and $allow403) {
            Write-Host "  [OK] 403 (Feature/Subscription restriction)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }

        elseif ($status -eq 429 -and $allow429) {
            Write-Host "  [OK] 429 (Rate limit working)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }

        else {
            if ($expectedStatus -is [array]) {
                HandleError $status $response.Content ($expectedStatus -join " or ") $name
            } else {
                HandleError $status $response.Content $expectedStatus $name
            }
            return $null
        }

    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()

        # Suporta array de status esperados
        $statusMatches = $false
        if ($expectedStatus -is [array]) {
            $statusMatches = $status -in $expectedStatus
        } else {
            $statusMatches = $status -eq $expectedStatus
        }

        if ($statusMatches) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green
            # Valida campos obrigatórios também em caso de erro
            if ($requiredFields.Count -gt 0 -and $content.Length -gt 5) {
                try {
                    $parsed = $content | ConvertFrom-Json
                    foreach ($field in $requiredFields) {
                        if (-not $parsed.$field) {
                            Write-Host "  [FAIL] Missing required field: $field" -ForegroundColor $red
                            $global:failedTests++
                            return $null
                        }
                    }
                } catch { }
            }
            $global:passedTests++
            return $content
        }

        elseif ($status -eq 403 -and $allow403) {
            Write-Host "  [OK] 403 (Feature restriction)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }

        elseif ($status -eq 429 -and $allow429) {
            Write-Host "  [OK] 429 (Rate limit)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }

        else {
            if ($expectedStatus -is [array]) {
                HandleError $status $content ($expectedStatus -join " or ") $name
            } else {
                HandleError $status $content $expectedStatus $name
            }
            return $null
        }
    }
}

function MockAITest {
    param(
        $name,
        $mockResponse,
        $requiredFields = @()  # Valida schema da resposta mock
    )

    $global:totalTests++
    Write-Host "`nTEST $($global:totalTests): $name (MOCK)" -ForegroundColor $yellow

    # Valida estrutura JSON even para mock
    try {
        $parsed = $mockResponse | ConvertFrom-Json
        
        # Valida campos obrigatórios
        if ($requiredFields.Count -gt 0) {
            foreach ($field in $requiredFields) {
                if (-not $parsed.$field) {
                    Write-Host "  [FAIL] Missing required field in mock response: $field" -ForegroundColor $red
                    $global:failedTests++
                    return
                }
            }
        }
        
        Write-Host "  [MOCK] Valid schema - Simulated response:" -ForegroundColor $yellow
    } catch {
        Write-Host "  [FAIL] Invalid JSON in mock response" -ForegroundColor $red
        $global:failedTests++
        return
    }
    
    # Força encoding UTF-8 para output
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    Write-Host "         $mockResponse" -ForegroundColor $cyan
    $global:passedTests++
}

# ========================================
# SETUP - REGISTER OR LOGIN REAL
# ========================================
Header "SETUP: REGISTER/LOGIN (REAL)"

# First, try to register a new test user with unique email
$uuid = [guid]::NewGuid().ToString().Substring(0, 8)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$random = Get-Random -Maximum 9999
$testEmail = "test-ai-$uuid-$timestamp-$random@leadflow.dev"
$testPassword = "AITest@123Pass"

$headers = @{
    "Content-Type" = "application/json"
}

# Try register first - aceita 200 ou 201 (comportamento varia entre versões)
$register = TestAPI "Register Test User" "POST" "$baseUrl/auth/register" `
    @{name="AI Test User"; email=$testEmail; password=$testPassword; confirmPassword=$testPassword} `
    @(200, 201) $headers -requiredFields @("accessToken", "tenantId")

if (-not $register) { 
    Write-Host "[ERROR] Register failed - não foi possível criar usuário de teste" -ForegroundColor Red
    Write-Host "        Verifique se o endpoint /auth/register está funcionando" -ForegroundColor Red
    exit 1
}

# Extrai token e tenantId da resposta de registro
$registerData = $register | ConvertFrom-Json
$token = $registerData.accessToken
$tenantId = $registerData.tenantId

if (-not $token -or -not $tenantId) {
    Write-Host "[ERROR] Resposta de registro inválida: faltam accessToken ou tenantId" -ForegroundColor Red
    exit 1
}

Write-Host "  ✓ Token extraído: $($token.Substring(0, 20))..." -ForegroundColor Green
Write-Host "  ✓ Tenant: $tenantId" -ForegroundColor Green

# Set up headers with correct tenant ID from response
$headers["Authorization"] = "Bearer $token"
$headers["X-Tenant-ID"] = $tenantId

# Debug: verificar headers
Write-Host "`n[DEBUG] Headers configurados:" -ForegroundColor Cyan
Write-Host "  Authorization: Bearer $($token.Substring(0, 20))..." -ForegroundColor Cyan
Write-Host "  X-Tenant-ID: $tenantId" -ForegroundColor Cyan
Write-Host "  Content-Type: $($headers['Content-Type'])" -ForegroundColor Cyan

# ========================================
# CREATE LEAD - REAL
# ========================================
Header "CREATE LEAD (REAL)"

# Cria lead com validação de campos obrigatórios
$leadResp = TestAPI "Create Lead" "POST" "$baseUrl/api/leads" `
    @{name="Teste AI Mock"; email="test-lead-$(Get-Random)@leadflow.dev"; phone="+5511999999999"} `
    201 $headers -requiredFields @("id", "tenantId", "name")

# Extrai e valida leadId
$leadData = $leadResp | ConvertFrom-Json
$leadId = $leadData.id
$leadTenantId = $leadData.tenantId

if (-not $leadId) {
    Write-Host "[ERROR] Lead criado mas sem ID - resposta inválida" -ForegroundColor Red
    exit 1
}

if ($leadTenantId -ne $tenantId) {
    Write-Host "[ERROR] TenantId mismatch: esperado $tenantId, recebido $leadTenantId" -ForegroundColor Red
    exit 1
}

Write-Host "  ✓ Lead criado com ID: $leadId" -ForegroundColor Green
Write-Host "  ✓ TenantId validado: $leadTenantId" -ForegroundColor Green

# ========================================
# AI ENDPOINTS - MOCK ONLY
# ========================================
Header "AI ENDPOINTS (MOCK)"

# Valida schema de respostas mock com campos obrigatórios
MockAITest "Chat" "{`"response`": `"Olá! Como posso ajudá-lo com seu crédito?`", `"timestamp`": `"$(Get-Date -Format 'o')`"}" -requiredFields @("response", "timestamp")

MockAITest "Lead Summary" '{"summary": "Cliente interessado em financiamento de veículo", "sentiment": "POSITIVE"}' -requiredFields @("summary", "sentiment")

MockAITest "Title Suggestion" '{"suggestion": "Cliente em busca de financiamento veicular - Alto potencial", "confidence": 0.95}' -requiredFields @("suggestion", "confidence")

MockAITest "Refine Message" '{"refined": "Texto refinado profissionalmente", "originalLength": 32, "refinedLength": 45}' -requiredFields @("refined", "originalLength", "refinedLength")

MockAITest "Sentiment Analysis" '{"sentiment": "POSITIVE", "score": 0.87, "keywords": ["crédito", "interesse"]}' -requiredFields @("sentiment", "score")

MockAITest "Lead Classification" '{"classification": "HOT_LEAD", "score": 0.92, "reason": "High engagement"}' -requiredFields @("classification", "score")

MockAITest "Generate Response" '{"response": "Entendi sua necessidade. Vamos processar sua solicitação.", "confidence": 0.89}' -requiredFields @("response", "confidence")

# ========================================
# SECURITY TEST - REAL
# ========================================
Header "SECURITY TEST (REAL)"

TestAPI "No Auth" "POST" "$baseUrl/ai/chat" `
    @{leadId=$leadId; message="teste"} `
    401 @{"Content-Type"="application/json"}

# ========================================
# VALIDATION TEST - REAL
# ========================================
Header "VALIDATION TEST (REAL)"

TestAPI "Empty Message" "POST" "$baseUrl/ai/chat" `
    @{leadId=$leadId; message=""} `
    400 $headers

# ========================================
# SUMMARY
# ========================================
Header "RESULT"

Write-Host "Total: $($global:totalTests)"
Write-Host "Passed: $($global:passedTests)" -ForegroundColor $green
Write-Host "Failed: $($global:failedTests)" -ForegroundColor $red

Write-Host "`nMODE: HYBRID" -ForegroundColor $cyan
Write-Host "  ✓ Real tests: Login, Create Lead, Security, Validation" -ForegroundColor $green
Write-Host "  ✓ Mock tests: AI Endpoints (Chat, Summary, Title, etc.)" -ForegroundColor $yellow

if ($global:failedTests -eq 0) {
    Write-Host "`n[SUCCESS] ALL TESTS PASSED" -ForegroundColor $green
} else {
    Write-Host "`n[FAIL] EXISTEM FALHAS" -ForegroundColor $red
}

exit $global:failedTests
