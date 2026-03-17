$token = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJiZmE3MzJlNC04ZjRmLTQ1ZGQtOTE2OC0zMjUwMjhlYzEzYTQiLCJzdWIiOiJjYXJsb3NAbGVhZGZsb3cuY29tIiwiaXNzIjoibGVhZGZsb3ciLCJpYXQiOjE3NzM3NjgxNzUsImV4cCI6MTc3Mzc3MTc3NSwidXNlcklkIjoiY2YyNWE1ZGMtMWRiNC00NzlhLTlkODUtZGNmOThmNzc5NjA5Iiwicm9sZSI6IlJPTEVfVVNFUiIsInRlbmFudCI6InB1YmxpYyJ9.TOXcNIAL_WwFMVOgVYbmRzmygexz4Pz548KJqVFRums"

$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

$body = @{
    vendorName = "Valid Vendor"
    whatsapp = "+5511987654321"
    companyName = "Valid Company"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Welcome message"
} | ConvertTo-Json

Write-Host "Testing PUT /api/me/settings" -ForegroundColor Cyan
Write-Host "Body: $body" -ForegroundColor Yellow

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $headers -Body $body -UseBasicParsing
    Write-Host "Status: $($response.StatusCode) OK" -ForegroundColor Green
    Write-Host "Response:" -ForegroundColor Green
    $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 2
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode) FAIL" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
