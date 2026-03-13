$headers = @{ "X-Tenant-ID" = "public" }
$body = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json

Write-Host "Testando login..."

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8081/auth/login" -Method POST -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 10
    Write-Host "✓ LOGIN SUCESSO 200"
    Write-Host "Response: "
    $response | ConvertTo-Json
} catch {
    Write-Host "X Erro: $($_.Exception.Response.StatusCode)"
    Write-Host "Message: $($_.Exception.Message)"
}