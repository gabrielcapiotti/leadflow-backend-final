$loginBody = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

try {
    $loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody
    $token = ($loginResp.Content | ConvertFrom-Json).token
    Write-Host "Token: $token"
} catch {
    Write-Host "Erro no login: $($_.Exception.Message)"
}
