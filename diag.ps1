$API = "http://localhost:8081"
$EMAIL = "test$(Get-Random)@test.com"
$PASS = "Test123!@#"

# Register
$reg = @{ email=$EMAIL; password=$PASS; firstName="T"; lastName="U" } | ConvertTo-Json
$r1 = Invoke-WebRequest -Uri "$API/api/auth/register" -Method Post -ContentType "application/json" -Body $reg -ErrorAction SilentlyContinue
Write-Host "Register: $($r1.StatusCode)"

# Login
$log = @{ email=$EMAIL; password=$PASS } | ConvertTo-Json
$r2 = Invoke-WebRequest -Uri "$API/api/auth/login" -Method Post -ContentType "application/json" -Body $log -ErrorAction SilentlyContinue
$data = $r2.Content | ConvertFrom-Json
$tok = $data.token
Write-Host "Login: $($r2.StatusCode)"

# Create Lead
$lead = @{ name="Test"; email="lead$(Get-Random)@test.com"; phone="+5511999999" } | ConvertTo-Json
$headers = @{ "Authorization"="Bearer $tok"; "Content-Type"="application/json" }

try {
    $r3 = Invoke-WebRequest -Uri "$API/api/leads" -Method Post -Headers $headers -Body $lead
    Write-Host "Lead Created: $($r3.StatusCode)"
} catch {
    Write-Host "Lead Error: $($_.Exception.Response.StatusCode)"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = [System.IO.StreamReader]::new($stream)
    $content = $reader.ReadToEnd()
    Write-Host "Response: $content"
}
