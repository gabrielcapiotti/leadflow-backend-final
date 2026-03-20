$BaseUrl = "http://localhost:8081"

# Register user
$regBody = @{name="Debug User"; email="debug-$(Get-Random)@test.com"; password="Test123!@"; confirmPassword="Test123!@"} | ConvertTo-Json

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method POST -Headers @{"Content-Type"="application/json";"X-Tenant-Id"="public"} -Body $regBody
    $token = $r.accessToken
    Write-Host "Token obtained: $($token.Substring(0,30))..."
} catch { Write-Host "Register failed: $_"; exit }

# Try to create lead
$leadBody = @{name="Test"; email="test@test.com"; phone="+5511"} | ConvertTo-Json
Write-Host "Creating lead..."  

try {
    $r = Invoke-RestMethod -Uri "$BaseUrl/api/leads" -Method POST -Headers @{
        "Content-Type"="application/json"
        "X-Tenant-Id"="public"
        "Authorization"="Bearer $token"
    } -Body $leadBody
    Write-Host "SUCCESS: $r"
} catch {
    Write-Host "FAILED"
    Write-Host "Status: $($_.Exception.Response.StatusCode)"
    Write-Host "Message: $($_.Exception.Message)"
    if ($_.Exception.Response.StatusCode -ne 200) {
        try {
            $resp = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($resp)
            $body = $reader.ReadToEnd()
            Write-Host "Response Body:"
            Write-Host $body
        } catch {}
    }
}
