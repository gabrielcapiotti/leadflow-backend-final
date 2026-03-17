$uri = "http://localhost:8081/health"
Write-Host "Testando $uri..."
$resp = Invoke-WebRequest -Uri $uri -UseBasicParsing
Write-Host "Status: $($resp.StatusCode)"
Write-Host "Content: $($resp.Content)"
