# Quick test: POST fails but GET works
$tok = (Invoke-RestMethod http://localhost:8081/auth/login -Method POST -Headers @{"X-Tenant-ID"="public"} -Body '{"email":"jwttest917066898@test.com","password":"TestJwt@123"}' -ContentType application/json).accessToken

Write-Host "Testing..."
Write-Host "GET:"
Invoke-WebRequest http://localhost:8081/api/leads -Method GET -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"="public"} -UseBasicParsing | Select-Object StatusCode

Write-Host "POST:"
Invoke-WebRequest http://localhost:8081/api/leads -Method POST -Headers @{"Authorization"="Bearer $tok";"X-Tenant-ID"="public";"Content-Type"="application/json"} -Body '{"name":"L","email":"l@t.com","phone":"1"}' -UseBasicParsing | Select-Object StatusCode
