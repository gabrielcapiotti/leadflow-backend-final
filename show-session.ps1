$BaseUrl = "http://localhost:8081"
$Email = "user$(Get-Random -Min 10000 -Max 99999)@test.com"
$Password = "Password@123"

# REGISTER
$body = @{
    name = "Test User"
    email = $Email
    password = $Password
    confirmPassword = $Password
} | ConvertTo-Json

$resp = Invoke-RestMethod "$BaseUrl/auth/register" -Method POST `
    -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} `
    -Body $body

$Token = $resp.accessToken

# LOGIN
$body = @{
    email = $Email
    password = $Password
} | ConvertTo-Json

$resp = Invoke-RestMethod "$BaseUrl/auth/login" -Method POST `
    -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} `
    -Body $body

$Token = $resp.accessToken

# GET /auth/sessions
$sessions = Invoke-RestMethod "$BaseUrl/auth/sessions" -Method GET `
    -Headers @{"Authorization"="Bearer $Token";"X-Tenant-ID"="public"}

Write-Host "====== SESSAO LISTADA ======" -ForegroundColor Cyan
Write-Host ""
$sessions | ConvertTo-Json -Depth 10
