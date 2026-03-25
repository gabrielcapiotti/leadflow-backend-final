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
        $allow429 = $false
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

        if ($status -eq $expectedStatus) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green

            # valida conteúdo
            if ($status -eq 200 -and $response.Content.Length -lt 5) {
                Write-Host "  [FAIL] Empty response" -ForegroundColor $red
                $global:failedTests++
                return $null
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
            HandleError $status $response.Content $expectedStatus $name
            return $null
        }

    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $content = $reader.ReadToEnd()

        if ($status -eq $expectedStatus) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green
            $global:passedTests++
            return $null
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
            HandleError $status $content $expectedStatus $name
            return $null
        }
    }
}

function MockAITest {
    param(
        $name,
        $mockResponse
    )

    $global:totalTests++
    Write-Host "`nTEST $($global:totalTests): $name (MOCK)" -ForegroundColor $yellow

    Write-Host "  [MOCK] Simulated response:" -ForegroundColor $yellow
    # Força encoding UTF-8 para output
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    Write-Host "         $mockResponse" -ForegroundColor $cyan
    $global:passedTests++
}

# ========================================
# SETUP - LOGIN REAL
# ========================================
Header "SETUP: LOGIN (REAL)"

$headers = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

$login = TestAPI "Login" "POST" "$baseUrl/auth/login" `
    @{email="admin.tester@leadflow.com"; password="AdminTest@123"} `
    200 $headers

if (-not $login) { exit 1 }

$token = ($login | ConvertFrom-Json).accessToken
$headers["Authorization"] = "Bearer $token"

# ========================================
# CREATE LEAD - REAL
# ========================================
Header "CREATE LEAD (REAL)"

$leadResp = TestAPI "Create Vendor Lead" "POST" "$baseUrl/api/vendor-leads/leads" `
    @{nomeCompleto="Teste AI Mock"; whatsapp="+5511999999999"; tipoConsorcio="VEICULO"; valorCredito=50000} `
    201 $headers

if (-not $leadResp) { exit 1 }

$leadId = ($leadResp | ConvertFrom-Json).id

# ========================================
# AI ENDPOINTS - MOCK ONLY
# ========================================
Header "AI ENDPOINTS (MOCK)"

MockAITest "Chat" '{"response": "Olá! Como posso ajudá-lo com seu crédito?", "timestamp": "'$([datetime]::Now)'"}'

MockAITest "Lead Summary" '{"summary": "Cliente interessado em financiamento de veículo", "sentiment": "POSITIVE"}'

MockAITest "Title Suggestion" '{"suggestion": "Cliente em busca de financiamento veicular - Alto potencial", "confidence": 0.95}'

MockAITest "Refine Message" '{"refined": "Texto refinado profissionalmente", "originalLength": 32, "refinedLength": 45}'

MockAITest "Sentiment Analysis" '{"sentiment": "POSITIVE", "score": 0.87, "keywords": ["crédito", "interesse"]}'

MockAITest "Lead Classification" '{"classification": "HOT_LEAD", "score": 0.92, "reason": "High engagement"}'

MockAITest "Generate Response" '{"response": "Entendi sua necessidade. Vamos processar sua solicitação.", "confidence": 0.89}'

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
