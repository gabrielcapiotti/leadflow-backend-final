$loginUrl = "http://localhost:8081/auth/login"
$loginData = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

Write-Host "Tentando login em $loginUrl..." -ForegroundColor Cyan
Write-Host "Dados: $loginData" -ForegroundColor Gray

try {
    $response = Invoke-WebRequest -Uri $loginUrl `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginData `
        -UseBasicParsing
    
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Resposta:" -ForegroundColor Green
    Write-Host ($response.Content)
} catch {
    Write-Host "Erro: $_" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        $reader.Close()
        
        Write-Host "Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        Write-Host "Response Body:" -ForegroundColor Red
        Write-Host $body
    }
}
