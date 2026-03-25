#!/usr/bin/env pwsh

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

        $response = Invoke-WebRequest @params
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

# ========================================
# SETUP
# ========================================
Header "SETUP: LOGIN"

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
# CREATE LEAD
# ========================================
Header "CREATE LEAD"

$leadResp = TestAPI "Create Vendor Lead" "POST" "$baseUrl/api/vendor-leads/leads" `
    @{nomeCompleto="Teste AI"; whatsapp="+5511999999999"; tipoConsorcio="VEICULO"; valorCredito=50000} `
    201 $headers

if (-not $leadResp) { exit 1 }

$leadId = ($leadResp | ConvertFrom-Json).id

# ========================================
# AI TESTS
# ========================================
Header "AI ENDPOINTS"

TestAPI "Chat" "POST" "$baseUrl/ai/chat" `
    @{leadId=$leadId; message="teste"} `
    200 $headers $true $true

TestAPI "Lead Summary" "POST" "$baseUrl/ai/lead-summary?leadId=$leadId" `
    $null 200 $headers $true $true

TestAPI "Title Suggestion" "POST" "$baseUrl/ai/title-suggestion?leadId=$leadId" `
    $null 200 $headers $true $true

TestAPI "Refine Message" "POST" "$baseUrl/ai/refine-message?message=teste" `
    $null 200 $headers $true $true

TestAPI "Sentiment" "POST" "$baseUrl/ai/sentiment-analysis?leadId=$leadId" `
    $null 200 $headers $true $true

TestAPI "Classify" "POST" "$baseUrl/ai/classify-lead?leadId=$leadId" `
    $null 200 $headers $true $true

TestAPI "Generate Response" "POST" "$baseUrl/ai/generate-response?leadId=$leadId&prompt=teste" `
    $null 200 $headers $true $true

# ========================================
# SECURITY TEST
# ========================================
Header "SECURITY TEST"

TestAPI "No Auth" "POST" "$baseUrl/ai/chat" `
    @{leadId=$leadId; message="teste"} `
    401 @{"Content-Type"="application/json"}

# ========================================
# VALIDATION TEST
# ========================================
Header "VALIDATION TEST"

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

if ($global:failedTests -eq 0) {
    Write-Host "`n[SUCCESS] TEST SUITE PASSOU" -ForegroundColor $green
} else {
    Write-Host "`n[FAIL] EXISTEM FALHAS" -ForegroundColor $red
}

exit $global:failedTests