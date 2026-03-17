$baseUrl = "http://localhost:8081"
$headers = @{"Content-Type" = "application/json"}

# Login
$loginResp = Invoke-WebRequest -Uri "$baseUrl/auth/login" -Method POST -Headers $headers -Body (@{email="carlos@leadflow.com"; password="SenhaForte@123"} | ConvertTo-Json) -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).accessToken
$headers["Authorization"] = "Bearer $token"

# Get leads
$leadsResp = Invoke-WebRequest -Uri "$baseUrl/vendor-leads?page=0`&size=10" -Method GET -Headers $headers -UseBasicParsing
$leads = $leadsResp.Content | ConvertFrom-Json

if ($leads.content -and $leads.content.Count -gt 0) {
    Write-Host "Found leads:" -ForegroundColor Green
    $leads.content | ForEach-Object { 
        Write-Host "  ID: $($_.id)" -ForegroundColor Cyan
        Write-Host "  Name: $($_.nomeCompleto)" -ForegroundColor Cyan
        Write-Host "  ---" -ForegroundColor Gray
    }
    
    $leadId = $leads.content[0].id
    Write-Host "`nUsing Lead ID: $leadId" -ForegroundColor Yellow
} else {
    Write-Host "No leads found, creating one..." -ForegroundColor Yellow
    
    $newLeadResp = Invoke-WebRequest -Uri "$baseUrl/vendor-leads/leads" -Method POST -Headers $headers `
        -Body (@{
            nomeCompleto = "Test Lead AI"
            whatsapp = "+55 (11) 98765-4321"
            tipoConsorcio = "imovel"
            valorCredito = 350000
            urgencia = "analisando"
        } | ConvertTo-Json) -UseBasicParsing
    
    $newLead = $newLeadResp.Content | ConvertFrom-Json
    $leadId = $newLead.id
    Write-Host "Created Lead ID: $leadId" -ForegroundColor Green
}

# Save to file
$leadId | Out-File -FilePath "test-lead-id.txt" -Force
"Lead ID saved to test-lead-id.txt: $leadId"
