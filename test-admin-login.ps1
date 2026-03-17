Write-Host "Testing Admin Login..." -ForegroundColor Cyan
Write-Host ""

$uri = "http://localhost:8081/auth/login"
$body = @{
    email = "admin@leadflow.com"
    password = "Admin@123456"
} | ConvertTo-Json

Write-Host "Email: admin@leadflow.com" -ForegroundColor Yellow
Write-Host "Password: Admin@123456" -ForegroundColor Yellow
Write-Host ""

$headers = @{"Content-Type" = "application/json"}

try {
    Write-Host "⏳ Enviando solicitação de login..." -ForegroundColor Cyan
    $response = Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body -TimeoutSec 30
    
    Write-Host "✅ LOGIN SUCCESSFUL!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Response:" -ForegroundColor Cyan
    $response.Content | ConvertFrom-Json | ConvertTo-Json | Write-Host
    
    $token = ($response.Content | ConvertFrom-Json).accessToken
    Write-Host ""
    Write-Host "Access Token: $($token.Substring(0, 50))..." -ForegroundColor Green
    
} catch {
    $ex = $_.Exception
    if ($ex.Response) {
        $status = $ex.Response.StatusCode.Value__
        Write-Host "❌ LOGIN FAILED - HTTP $status" -ForegroundColor Red
        Write-Host ""
        $streamReader = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
        $body = $streamReader.ReadToEnd()
        $streamReader.Close()
        Write-Host "Response Body:" -ForegroundColor Yellow
        Write-Host $body -ForegroundColor Yellow
    } else {
        Write-Host "❌ Erro de conexão: $($ex.Message)" -ForegroundColor Red
    }
}
