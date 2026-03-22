$BaseUrl = "http://localhost:8081"  

# First login
Write-Host "Logging in..."
try {
    $loginResp = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method Post -Headers @{"Content-Type" = "application/json"} -Body (@{email="admin@leadflow.dev"; password="AdminPassword123!"} | ConvertTo-Json) -UseBasicParsing -ErrorAction Stop
    $loginData = $loginResp.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "Login successful!"
} catch {
    Write-Host "Login failed: $($_.Exception.Message)"
    exit 1
}

# Get list of all vendors
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/vendors" -Method Get -Headers @{
        "X-Tenant-Id" = "public"
        "Authorization" = "Bearer $token"
    } -UseBasicParsing -ErrorAction Stop
    $vendors = $resp.Content | ConvertFrom-Json
    
    Write-Host "Total vendors in public tenant: $($vendors.Count)"
    Write-Host "Vendors with 'vendor' in slug:"
    
    $vendors | Where-Object { $_.slug -like "*vendor*" } | Select-Object -First 10 | ForEach-Object { 
        Write-Host "  - ID: $($_.id), Slug: $($_.slug), Email: $($_.userEmail)"
    }
} catch {
    Write-Host "Error: $($_.Exception.Message)"
}
