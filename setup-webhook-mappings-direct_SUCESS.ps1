param(
    [string]$BaseUrl = "http://localhost:8081"
)

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Webhook Test Setup - Direct Stripe Mappings" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check server (ignora health check, só testa conexão)
Write-Host "Step 1: Checking server connection..." -ForegroundColor Yellow
$serverOk = $false

try {
    # Usar endpoint que sempre existe - testa apenas conectividade
    $request = [System.Net.HttpWebRequest]::Create("http://localhost:8081/api/actuator/health")
    $request.Method = "GET"
    $request.Timeout = 5000
    
    try {
        $response = $request.GetResponse()
        Write-Host "  OK - Server connected" -ForegroundColor Green
        $response.Close()
        $serverOk = $true
    } catch [System.Net.WebException] {
        # Servidor respondeu mas com erro HTTP (ex: 503 do Redis) - isso é OK, servidor está online
        if ($_.Exception.Response -ne $null) {
            Write-Host "  OK - Server connected (health degraded)" -ForegroundColor Green
            $serverOk = $true
        } else {
            Write-Host "  ERROR - Cannot connect to localhost:8081" -ForegroundColor Red
            exit 1
        }
    }
}
catch {
    Write-Host "  ERROR - Cannot connect to localhost:8081" -ForegroundColor Red
    exit 1
}

# Step 2: Get tenant ID for the request
Write-Host ""
Write-Host "Step 2: Getting tenant ID..." -ForegroundColor Yellow

$tenantId = $null
$getTenantUrl = "$BaseUrl/api/billing/test/get-tenant-id"

try {
    $tenantResponse = Invoke-WebRequest -Uri $getTenantUrl -UseBasicParsing -Method Get -TimeoutSec 10
    $tenantData = $tenantResponse.Content | ConvertFrom-Json
    $tenantId = $tenantData.tenantId
    Write-Host "  OK - Got tenant: $($tenantId.Substring(0, 8))..." -ForegroundColor Green
}
catch {
    Write-Host "  ERROR - Cannot get tenant ID" -ForegroundColor Red
    exit 1
}

# Step 3: Create mappings via REST endpoint
Write-Host ""
Write-Host "Step 3: Creating Stripe customer mappings..." -ForegroundColor Yellow

$setupUrl = "$BaseUrl/api/billing/test/create-stripe-mappings"
$setupOk = $false

$headers = @{
    "X-Tenant-Id" = $tenantId
    "Content-Type" = "application/json"
}

try {
    $response = Invoke-WebRequest -Uri $setupUrl -Method Post -UseBasicParsing -Headers $headers -TimeoutSec 30 -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    
    Write-Host "  OK - Mappings created" -ForegroundColor Green
    Write-Host ""
    Write-Host "Details:" -ForegroundColor Cyan
    Write-Host "  Total: $($data.count)/$($data.totalExpected)" -ForegroundColor Green
    Write-Host "  Tenant: $($data.tenantId)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Stripe Customers:" -ForegroundColor Cyan
    
    foreach ($item in $data.mappings) {
        Write-Host "    - $($item.customerId)" -ForegroundColor Green
    }
    
    $setupOk = $true
}
catch {
    $msg = $_.Exception.Message
    Write-Host "  ERROR - $msg" -ForegroundColor Red
}

if (-not $setupOk) {
    exit 1
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "Setup complete - Ready for webhook tests" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host ""
