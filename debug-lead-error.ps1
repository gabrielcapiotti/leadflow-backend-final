$API_URL = "http://localhost:8080"
$EMAIL = "test_$(Get-Random)@example.com"
$PASSWORD = "TempPassword123!@#"

Write-Host "🔍 DIAGNOSTIC: Capturing Lead Creation Error"
Write-Host "=========================================="

# Register
Write-Host "`n[1] Register User..."
$regBody = @{
    email       = $EMAIL
    password    = $PASSWORD
    firstName   = "Test"
    lastName    = "User"
} | ConvertTo-Json

try {
    $regResponse = Invoke-WebRequest -Uri "$API_URL/api/auth/register" `
        -Method Post `
        -ContentType "application/json" `
        -Body $regBody -ErrorAction Stop
    Write-Host "✅ Register OK"
} catch {
    Write-Host "❌ Register failed: $($_.Exception.Message)"
    exit 1
}

# Login
Write-Host "`n[2] Login..."
$loginBody = @{
    email    = $EMAIL
    password = $PASSWORD
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -Uri "$API_URL/api/auth/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body $loginBody -ErrorAction Stop
    $loginData = ($loginResponse.Content | ConvertFrom-Json)
    $TOKEN = $loginData.token
    Write-Host "✅ Login OK"
    Write-Host "Token: $($TOKEN.Substring(0, 20))..."
} catch {
    Write-Host "❌ Login failed: $($_.Exception.Message)"
    exit 1
}

# Create Lead
Write-Host "`n[3] Create Lead - CAPTURING FULL ERROR..."
$leadBody = @{
    name  = "John Doe"
    email = "john_$(Get-Random)@example.com"
    phone = "+5511999999999"
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $TOKEN"
    "Content-Type"  = "application/json"
}

try {
    $leadResponse = Invoke-WebRequest -Uri "$API_URL/api/leads" `
        -Method Post `
        -Headers $headers `
        -Body $leadBody `
        -ErrorAction Stop
    Write-Host "✅ Create Lead OK"
    $leadData = ($leadResponse.Content | ConvertFrom-Json)
    Write-Host "Lead ID: $($leadData.id)"
} catch {
    Write-Host "❌ Create Lead FAILED"
    Write-Host "`n📋 ERROR DETAILS:"
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)"
    Write-Host "Status Description: $($_.Exception.Response.StatusDescription)"
    
    # Try to capture response body
    try {
        $errStream = $_.Exception.Response.GetResponseStream()
        $reader = [System.IO.StreamReader]::new($errStream)
        $errContent = $reader.ReadToEnd()
        Write-Host "`n Response Body:"
        Write-Host $errContent
    } catch {
        Write-Host "Could not read error response body"
    }
}
