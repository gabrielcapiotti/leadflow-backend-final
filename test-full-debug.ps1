# Login e teste do PUT com resposta detalhada
$loginUrl = "http://localhost:8081/auth/login"
$settingsUrl = "http://localhost:8081/settings"

Write-Host "=== SETTINN GENDPOINTS TEST ===" -ForegroundColor Yellow

# Step 1: Login
Write-Host "`n[1/3] Autenticando..." -ForegroundColor Cyan
$loginData = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -Uri $loginUrl `
    -Method Post `
    -ContentType "application/json" `
    -Body $loginData `
    -UseBasicParsing
$loginBody = $loginResp.Content | ConvertFrom-Json
$token = $loginBody.accessToken
Write-Host "Token obtido: $($token.Substring(0, 50))..." -ForegroundColor Green

# Step 2: PUT /settings (create)
Write-Host "`n[2/3] Enviando PUT /settings..." -ForegroundColor Cyan
$updateData = @{
    vendorName = "Test Vendor"
    whatsapp = "11999999999"
    companyName = "Test Company"
    logo = "https://example.com/logo.jpg"
    welcomeMessage = "Welcome!"
} | ConvertTo-Json -Compress

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

Write-Host "Headers:" -ForegroundColor Gray
$headers | Format-Table -AutoSize

Write-Host "Body:" -ForegroundColor Gray
Write-Host $updateData

try {
    $putResp = Invoke-WebRequest -Uri $settingsUrl `
        -Method Put `
        -ContentType "application/json" `
        -Headers $headers `
        -Body $updateData `
        -UseBasicParsing
    
    Write-Host "✓ Status: $($putResp.StatusCode)" -ForegroundColor Green
    Write-Host "Resposta:" -ForegroundColor Green
    $putResp.Content | ConvertFrom-Json | ConvertTo-Json | Write-Host
    $settingId = ($putResp.Content | ConvertFrom-Json).id
    Write-Host "Setting ID: $settingId" -ForegroundColor Green
} catch {
    Write-Host "✗ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
            
            Write-Host "Resposta:" -ForegroundColor Red
            Write-Host $errorBody
        } catch {
            Write-Host "Erro ao ler resposta"
        }
    }
}

# Step 3: GET /settings
Write-Host "`n[3/3] Enviando GET /settings..." -ForegroundColor Cyan
try {
    $getResp = Invoke-WebRequest -Uri $settingsUrl `
        -Method Get `
        -Headers $headers `
        -UseBasicParsing
    
    Write-Host "✓ Status: $($getResp.StatusCode)" -ForegroundColor Green
    Write-Host "Resposta:" -ForegroundColor Green
    $getResp.Content | ConvertFrom-Json | ConvertTo-Json | Write-Host
} catch {
    Write-Host "✗ Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
            
            Write-Host "Resposta:" -ForegroundColor Red
            Write-Host $errorBody
        } catch {
            Write-Host "Erro ao ler resposta"
        }
    }
}

Write-Host "`n=== FIM DO TESTE ===" -ForegroundColor Yellow
