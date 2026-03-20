$body = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json

try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"; "X-Tenant-ID" = "public"} `
        -Body $body -UseBasicParsing
    Write-Host "Success: $($resp.StatusCode)" -ForegroundColor Green
    $resp.Content | ConvertFrom-Json | Format-Table
} catch {
    Write-Host "Error: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}
