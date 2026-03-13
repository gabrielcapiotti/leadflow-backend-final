$headers = @{"X-Tenant-ID"="public"}
$body = @{email="carlos@leadflow.com";password="SenhaForte@123"} | ConvertTo-Json
Write-Host "Testing login..."
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8081/auth/login" -Method POST -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 10
    Write-Host "SUCCESS 200" 
    $response | ConvertTo-Json
} catch {
    Write-Host "Error: $($_.Exception.Response.StatusCode)"
}
