$Email = "test$(Get-Random)@leadflow.com"
$Pass = "Password@12345"

# Test 1: Register
Write-Host "1. REGISTER" -ForegroundColor Cyan
$body = @{name="Test U";email=$Email;password=$Pass;confirmPassword=$Pass} | ConvertTo-Json
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/auth/register" -Method POST -Headers @{"Content-Type"="application/json";"X-Tenant-ID"="public"} -Body $body -UseBasicParsing
    Write-Host "SUCCESS: $($r.StatusCode)"
    $token = ($r.Content | ConvertFrom-Json).accessToken
    Write-Host "Token: $($token.Substring(0,20))..."
} catch {
    Write-Host "FAILED: $($_.Exception.Response.StatusCode.Value)"
}
