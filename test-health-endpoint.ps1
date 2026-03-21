Write-Host "Buscando um vendor ID válido..." -ForegroundColor Cyan

# Conectar ao banco de dados
$env:PGPASSWORD = "venusia"
$vendorId = & psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT id FROM vendors LIMIT 1;" | Select-Object -First 1 | ForEach-Object {$_.Trim()}

if ($vendorId) {
    Write-Host "Vendor ID encontrado: $vendorId" -ForegroundColor Green
    Write-Host ""
    Write-Host "Testando endpoint com este ID real..." -ForegroundColor Yellow
    
    # Login para obter token
    try {
        $loginResponse = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/login" `
            -Method POST `
            -ContentType "application/json" `
            -Body (@{email="admin@leadflow.com"; password="Admin@Lead123"} | ConvertTo-Json) `
            -ErrorAction SilentlyContinue
        
        if ($loginResponse.StatusCode -eq 200) {
            $token = ($loginResponse.Content | ConvertFrom-Json).accessToken
            $headers = @{"Authorization" = "Bearer $token"}
            
            # Testar o endpoint
            try {
                $response = Invoke-WebRequest -Uri "http://localhost:8081/api/admin/metrics/health/$vendorId" `
                    -Method GET `
                    -Headers $headers `
                    -ErrorAction Continue
                $statusCode = $response.StatusCode
                $body = $response.Content
            } catch {
                $statusCode = $_.Exception.Response.StatusCode.Value
                $body = $_.Exception.Response
            }
            
            Write-Host "Status: $statusCode" -ForegroundColor $(if ($statusCode -eq 200) { "Green" } else { "Red" })
            
            if ($statusCode -eq 200) {
                $obj = $body | ConvertFrom-Json
                Write-Host "Resposta:" -ForegroundColor Cyan
                Write-Host ($obj | ConvertTo-Json) -ForegroundColor Green
            } else {
                Write-Host "Erro: $body" -ForegroundColor Red
            }
        } else {
            Write-Host "Erro ao fazer login" -ForegroundColor Red
        }
    } catch {
        Write-Host "Exceção: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "Nenhum vendor encontrado no banco de dados!" -ForegroundColor Red
}
