param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "debug-$(Get-Random)@test.com",
    [string]$Password = "SenhaForte123!@#"
)

Write-Host "Registering user..." -ForegroundColor Cyan
$register = @{ 
    email = $Username
    password = $Password
    confirmPassword = $Password
    name = "Test User" 
} | ConvertTo-Json

$r = Invoke-WebRequest "$BaseUrl/api/auth/register" -Method POST -Body $register -ContentType "application/json" -UseBasicParsing
$json = $r.Content | ConvertFrom-Json
$TenantId = $json.tenantId
Write-Host "Registered: TenantId=$TenantId" -ForegroundColor Green

Write-Host "Logging in..." -ForegroundColor Cyan
$login = @{ 
    email = $Username
    password = $Password 
} | ConvertTo-Json

$r = Invoke-WebRequest "$BaseUrl/api/auth/login" -Method POST -Headers @{ "X-Tenant-ID" = $TenantId } -Body $login -ContentType "application/json" -UseBasicParsing
$json = $r.Content | ConvertFrom-Json
$Token = $json.accessToken
Write-Host "Logged in: Token=$($Token.Substring(0, 30))..." -ForegroundColor Green

Write-Host "Creating subscription..." -ForegroundColor Cyan
$headers = @{
    Authorization = "Bearer $Token"
    "X-Tenant-ID" = $TenantId
    "Content-Type" = "application/json"
}

$body = @{
    planId = "Leadflow Standard"
} | ConvertTo-Json

Write-Host "Request Body:" -ForegroundColor Yellow
Write-Host $body

try {
    $r = Invoke-WebRequest "$BaseUrl/api/billing/subscription" -Method POST -Headers $headers -Body $body -ContentType "application/json" -UseBasicParsing
    Write-Host "SUCCESS - Status: $($r.StatusCode)" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Green
    $r.Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
} catch {
    $code = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.Value__ } else { "Unknown" }
    Write-Host "FAILED - Status: $code" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = [System.IO.StreamReader]::new($stream)
        $errorBody = $reader.ReadToEnd()
        Write-Host "Error Response:" -ForegroundColor Red
        Write-Host $errorBody
    }
}
