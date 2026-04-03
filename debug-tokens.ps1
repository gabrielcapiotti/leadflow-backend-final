$BaseUrl = 'http://localhost:8081'

# Register
$resp = Invoke-WebRequest -Uri "$BaseUrl/api/auth/register" -Method POST -Body (@{
    email = 'test_debug@e2e.com'
    password = 'Test123!@#'
    confirmPassword = 'Test123!@#'
    name = 'Debug User'
} | ConvertTo-Json) -Headers @{'Content-Type' = 'application/json'} -UseBasicParsing

$data = $resp.Content | ConvertFrom-Json
Write-Host 'Register Response:' -ForegroundColor Cyan
$data | ConvertTo-Json -Depth 3

$tenantId = $data.tenantId
$token = $data.token

Write-Host "`nTenantId: $tenantId" -ForegroundColor Yellow
Write-Host "Register Token: $($token.Substring(0,30))..." -ForegroundColor Gray
