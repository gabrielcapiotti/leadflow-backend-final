$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

function Write-Title {
    Write-Host "`nLEADFLOW API TEST SUITE`n" -ForegroundColor Cyan
}

function Write-Step {
    param($Number, $Text)
    Write-Host "[$Number] $Text" -ForegroundColor Yellow
}

function Write-Success {
    param($Text, $Status)
    Write-Host "   OK - $Text (HTTP $Status)" -ForegroundColor Green
}

function Write-Fail {
    param($Text, $Status, $Error)
    Write-Host "   FAIL - $Text (HTTP $Status)" -ForegroundColor Red
    if ($Error) {
        Write-Host "   Error: $Error" -ForegroundColor DarkRed
    }
}

$AccessToken = $null
$RefreshToken = $null
$LeadId = $null
$VendorLeadId = $null
$TestResults = @()

function Invoke-ApiRequest {
    param($Method, $Endpoint, $Body = $null, $Auth = $false)

    $Headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantHeader
    }

    if ($Auth -and $AccessToken) {
        $Headers["Authorization"] = "Bearer $AccessToken"
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

        return @{
            Success = $true
            Status  = 200
            Data    = $response
        }
    }
    catch {
        $status = 0
        try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
        
        $errorMsg = $null
        try { 
            $errorMsg = $_.ErrorDetails.Message 
        } catch {}

        return @{
            Success = $false
            Status  = $status
            Error   = $errorMsg
            Exception = $_.Exception.Message
        }
    }
}

function Test-Step {
    param($Name, $Response)

    if ($Response.Success) {
        Write-Success $Name $Response.Status
        $TestResults += "PASS"
    } else {
        Write-Fail $Name $Response.Status $Response.Error
        if ($Response.Exception) {
            Write-Host "Exception: $($Response.Exception)" -ForegroundColor DarkGray
        }
        $TestResults += "FAIL"
        # Don't exit, continue testing
    }
}

function Show-Summary {
    $total = $TestResults.Count
    $passed = ($TestResults | Where-Object { $_ -eq "PASS" }).Count

    Write-Host "`nTEST SUMMARY" -ForegroundColor Cyan
    Write-Host "Passed: $passed/$total" -ForegroundColor Green
    Write-Host "Failed: $($total - $passed)" -ForegroundColor Red
}

$startTime = Get-Date
Write-Title

Write-Step 1 "Health Check"
$r = Invoke-ApiRequest "GET" "/actuator/health"
Test-Step "Health Check" $r

Write-Step 2 "Register"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$email = "test-$timestamp@leadflow.dev"
$password = "SecurePass123!@"

$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Test"
    email = $email
    password = $password
    confirmPassword = $password
}
Test-Step "Register" $r
$AccessToken = $r.Data.accessToken
$RefreshToken = $r.Data.refreshToken
Write-Host "Token obtained: $($AccessToken.Substring(0,20))..." -ForegroundColor DarkGray

Write-Step 3 "Login"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $email
    password = $password
}
Test-Step "Login" $r
$AccessToken = $r.Data.accessToken
$RefreshToken = $r.Data.refreshToken

Write-Step 4 "Get User"
$r = Invoke-ApiRequest "GET" "/auth/me" $null $true
Test-Step "Get User" $r

Write-Step 5 "Create Lead"
$r = Invoke-ApiRequest "POST" "/leads" @{
    name = "Lead Test"
    email = "lead-$timestamp@email.com"
    phone = "999999999"
    tipoConsorcio = "IMOVEL"
    valorCredito = "200000"
    urgencia = "quero_fechar"
} $true
Test-Step "Create Lead" $r
$LeadId = $r.Data.id

Write-Step 6 "List Leads"
$r = Invoke-ApiRequest "GET" "/leads" $null $true
Test-Step "List Leads" $r

Write-Step 7 "Create Vendor Lead"
Write-Host "Token for step 7: $($AccessToken.Substring(0,30))..." -ForegroundColor DarkGray
$r = Invoke-ApiRequest "POST" "/vendor-leads/leads" @{
    nomeCompleto = "Maria"
    whatsapp = "999999999"
    tipoConsorcio = "VEICULO"
    valorCredito = "100000"
    urgencia = "quero_fechar"
} $true
Test-Step "Create Vendor Lead" $r
$VendorLeadId = $r.Data.id

Write-Step 8 "Vendor Leads"
Write-Host "Token for step 8: $($AccessToken.Substring(0,30))..." -ForegroundColor DarkGray
$endpoint = "/vendor-leads?page=0" + [char]38 + "size=10"
$r = Invoke-ApiRequest "GET" $endpoint $null $true
Test-Step "Vendor Leads" $r

Write-Step 9 "Update Stage"
$r = Invoke-ApiRequest "PUT" "/vendor-leads/$VendorLeadId/stage" @{
    stage = "QUALIFIED"
} $true
Test-Step "Update Stage" $r

Write-Step 10 "Refresh Token"
$r = Invoke-ApiRequest "POST" "/auth/refresh" @{
    refreshToken = $RefreshToken
}
Test-Step "Refresh Token" $r

Write-Step 11 "Delete Lead"
$r = Invoke-ApiRequest "DELETE" "/leads/$LeadId" $null $true
Test-Step "Delete Lead" $r

$duration = ((Get-Date) - $startTime).TotalSeconds

Show-Summary

Write-Host "`nTotal time: $duration s" -ForegroundColor Cyan
Write-Host "Test email: $email" -ForegroundColor DarkCyan
