Write-Host "TEST: User DELETE endpoint" -ForegroundColor Cyan

# 1. Register
$resp = @{
    email = "test$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json | Invoke-WebRequest http://localhost:8081/auth/register -Method POST -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} -UseBasicParsing | ConvertFrom-Json

$token = $resp.accessToken
$userId = $resp.userId

Write-Host "User: $userId" -ForegroundColor Green
Write-Host "Token length: $($token.Length)" -ForegroundColor Green

# 2. GET own user (should work)
Write-Host "`nGET /users/$userId" -ForegroundColor Cyan
$getResp = Invoke-WebRequest http://localhost:8081/users/$userId `
    -Method GET `
    -Headers @{Authorization="Bearer $token";"X-Tenant-ID"="public"} `
    -UseBasicParsing
Write-Host "Status: $($getResp.StatusCode)" -ForegroundColor Green

# 3. DELETE own user
Write-Host "`nDELETE /users/$userId" -ForegroundColor Cyan
try {
    $deleteResp = Invoke-WebRequest http://localhost:8081/users/$userId `
        -Method DELETE `
        -Headers @{Authorization="Bearer $token";"X-Tenant-ID"="public"} `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Host "Status: $($deleteResp.StatusCode)" -ForegroundColor Green
} catch {
    $code = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $code" -ForegroundColor Red
    
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = [System.IO.StreamReader]::new($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Response: $body" -ForegroundColor Yellow
    }
}
