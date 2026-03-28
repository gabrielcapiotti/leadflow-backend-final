$baseUrl = "http://localhost:8081"
$tenantId = "550e8400-e29b-41d4-a716-446655440000"

# Register
$r = Invoke-WebRequest -Uri "$baseUrl/api/auth/register" -Method POST -Headers @{"X-Tenant-ID"=$tenantId} -ContentType "application/json" -Body (@{email="test$(Get-Random)@test.com";password="Test@1234";companyName="Co";phone="+5511999999999"}|ConvertTo-Json) -UseBasicParsing
$vendor = $r.Content | ConvertFrom-Json

# Login
$l = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" -Method POST -Headers @{"X-Tenant-ID"=$tenantId} -ContentType "application/json" -Body (@{email=$vendor.email;password="Test@1234"}|ConvertTo-Json) -UseBasicParsing
$auth = $l.Content | ConvertFrom-Json
$token = $auth.accessToken

# GET before
Write-Host "GET BEFORE CREATE:"
$g1 = Invoke-WebRequest -Uri "$baseUrl/api/billing/subscription" -Method GET -Headers @{"Authorization"="Bearer $token";"X-Tenant-ID"=$tenantId} -UseBasicParsing
Write-Host "Status: $($g1.StatusCode), Length: $($g1.Content.Length)"
if ($g1.Content.Length -gt 0) { $g1.Content | ConvertFrom-Json | ConvertTo-Json -Depth 3 }

# POST
Write-Host "`nPOST CREATE:"
$p = Invoke-WebRequest -Uri "$baseUrl/api/billing/subscription" -Method POST -Headers @{"Authorization"="Bearer $token";"X-Tenant-ID"=$tenantId} -ContentType "application/json" -Body (@{planId="Leadflow Standard"}|ConvertTo-Json) -UseBasicParsing
Write-Host "Status: $($p.StatusCode)"

# GET after
Write-Host "`nGET AFTER CREATE:"
$g2 = Invoke-WebRequest -Uri "$baseUrl/api/billing/subscription" -Method GET -Headers @{"Authorization"="Bearer $token";"X-Tenant-ID"=$tenantId} -UseBasicParsing
Write-Host "Status: $($g2.StatusCode), Length: $($g2.Content.Length)"
if ($g2.Content.Length -gt 0) { $g2.Content | ConvertFrom-Json | ConvertTo-Json -Depth 3 }
