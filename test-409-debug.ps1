$baseUrl = "http://localhost:8081/api"
$email = "test-409debug-$(Get-Random)@leadflow.dev"

# Register
$registerBody = @{
    name = "Test 409"
    email = $email
    password = "Test@1234"
} | ConvertTo-Json

$registerResp = Invoke-WebRequest -Uri "$baseUrl/auth/register" -Method POST -Headers @{"Content-Type" = "application/json"} -Body $registerBody -ErrorAction SilentlyContinue

# Login
$loginBody = @{
    email = $email
    password = "Test@1234"
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method POST -Headers @{"Content-Type" = "application/json"} -Body $loginBody -ErrorAction SilentlyContinue
$token = ($loginResp.Content | ConvertFrom-Json).token
Write-Host "Token: $token"

# Create Vendor Lead
$leadBody = @{
    nomeCompleto = "Test 409"
    whatsapp = "11999999999"
    tipoConsorcio = "VEICULO"
    valorCredito = "100000"
    urgencia = "quero_fechar"
} | ConvertTo-Json

Write-Host "Creating vendor lead..."
try {
    $leadResp = Invoke-WebRequest -Uri "$baseUrl/vendor-leads/leads" `
        -Method POST `
        -Headers @{
            "Authorization" = "Bearer $token"
            "X-Tenant-Id" = "default"
            "Content-Type" = "application/json"
        } `
        -Body $leadBody `
        -ErrorAction Stop
    Write-Host "Success: $($leadResp.StatusCode)"
    Write-Host $leadResp.Content
} catch {
    Write-Host "Error: $($_.Exception.Response.StatusCode)"
    Write-Host "Response: $($_.Exception.Response)"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $error_response = $reader.ReadToEnd()
    Write-Host "Error details: $error_response"
}
