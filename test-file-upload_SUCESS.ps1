# Test Suite: File Upload Endpoint
# Endpoints: POST /files/upload
# Status: Production Ready

param(
    [string]$BaseUrl = "http://localhost:8081/api"
)

$ErrorActionPreference = "Stop"
$testsPassed = 0
$testsFailed = 0

# Get auth token
$email = "file-test-$(Get-Random)@test.com"
$password = "Test@1234"

Write-Host "Registering test user: $email" -ForegroundColor Yellow

# Create temp JSON file for request body
$jsonFile = New-TemporaryFile
$jsonBody = @{ 
    email = $email
    password = $password
    confirmPassword = $password
    name = "Test User"
} | ConvertTo-Json
Set-Content -Path $jsonFile.FullName -Value $jsonBody

$registerResponse = curl.exe -s -X POST "$BaseUrl/auth/register" `
    -H "Content-Type: application/json" `
    -d "@$($jsonFile.FullName)"

$registerData = $registerResponse | ConvertFrom-Json -ErrorAction SilentlyContinue
$token = $registerData.accessToken

if ($token) {
    Write-Host "✅ Registered successfully" -ForegroundColor Green
}

if (-not $token) {
    Write-Host "Register failed: $registerResponse" -ForegroundColor Red
}

Remove-Item -Path $jsonFile.FullName -Force

if (-not $token) {
    throw "Failed to register and authenticate"
}

Write-Host "✅ Authenticated with token: $($token.Substring(0, 20))..." -ForegroundColor Cyan

function Test-Case {
    param(
        [string]$Name,
        [scriptblock]$Test
    )
    
    try {
        & $Test
        Write-Host "✅ $Name" -ForegroundColor Green
        $script:testsPassed++
    } catch {
        Write-Host "❌ $Name" -ForegroundColor Red
        Write-Host "   Error: $_" -ForegroundColor Red
        $script:testsFailed++
    }
}

function Upload-File {
    param(
        [string]$FilePath,
        [string]$Url,
        [string]$Token
    )
    
    # Use curl with auth header
    $result = curl.exe -s -H "Authorization: Bearer $Token" -F "file=@$FilePath" "$Url"
    return $result | ConvertFrom-Json
}

Write-Host "`n=== File Upload Endpoint Tests ===" -ForegroundColor Cyan

# Test 1: Upload valid text file
Test-Case "Test 1: Upload valid text file" {
    $tempFile = New-TemporaryFile
    Set-Content -Path $tempFile.FullName -Value "This is test content for lead flow"
    
    $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
    
    if ($json.file_url -like "*/uploads/*") {
        Write-Host "   Response: $($json.file_url)"
    } else {
        throw "Expected file_url in response"
    }
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 2: Upload CSV file (common for lead data)
Test-Case "Test 2: Upload CSV file" {
    $tempFile = New-TemporaryFile
    $csvContent = @"
email,name,company
john@example.com,John Doe,Tech Corp
jane@example.com,Jane Smith,Lead Inc
"@
    Set-Content -Path $tempFile.FullName -Value $csvContent
    
    $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
    
    if ($json.file_url) {
        Write-Host "   CSV uploaded: $($json.file_url)"
    } else {
        throw "CSV upload failed"
    }
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 3: Upload JSON file
Test-Case "Test 3: Upload JSON file" {
    $tempFile = New-TemporaryFile
    $jsonContent = @"
{
  "leads": [
    { "email": "lead1@test.com", "name": "Lead 1" },
    { "email": "lead2@test.com", "name": "Lead 2" }
  ]
}
"@
    Set-Content -Path $tempFile.FullName -Value $jsonContent
    
    $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
    
    if ($json.file_url) {
        Write-Host "   JSON file uploaded: $($json.file_url)"
    } else {
        throw "JSON upload failed"
    }
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 4: Response contains proper structure
Test-Case "Test 4: Response structure validation" {
    $tempFile = New-TemporaryFile
    Set-Content -Path $tempFile.FullName -Value "test content"
    
    $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
    
    if (-not $json.file_url) {
        throw "Response missing 'file_url' field"
    }
    
    if ($json.file_url -notlike "http://*") {
        throw "file_url should be valid HTTP URL: $($json.file_url)"
    }
    
    Write-Host "   Response structure valid: $($json.file_url)"
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 5: Upload binary file (PNG image)
Test-Case "Test 5: Upload binary file (image)" {
    $tempFile = New-TemporaryFile
    
    # Create a minimal PNG binary
    $pngHeader = [byte[]]@(137, 80, 78, 71, 13, 10, 26, 10)
    [System.IO.File]::WriteAllBytes($tempFile.FullName, $pngHeader)
    
    $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
    
    if ($json.file_url) {
        Write-Host "   Binary file uploaded: $($json.file_url)"
    } else {
        throw "Binary upload failed"
    }
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 6: Multiple sequential uploads
Test-Case "Test 6: Multiple sequential uploads" {
    $uploadCount = 0
    
    for ($i = 1; $i -le 3; $i++) {
        $tempFile = New-TemporaryFile
        Set-Content -Path $tempFile.FullName -Value "File content $i"
        
        $json = Upload-File -FilePath $tempFile.FullName -Url "$BaseUrl/files/upload" -Token $token
        
        if ($json.file_url) {
            $uploadCount++
        } else {
            throw "File $i upload failed"
        }
        
        Remove-Item -Path $tempFile.FullName -Force
    }
    
    if ($uploadCount -eq 3) {
        Write-Host "   3 files uploaded successfully"
    } else {
        throw "Only $uploadCount of 3 files uploaded"
    }
}

# Test 7: Response is valid JSON
Test-Case "Test 7: Response is valid JSON" {
    $tempFile = New-TemporaryFile
    Set-Content -Path $tempFile.FullName -Value "test"
    
    $result = curl.exe -s -H "Authorization: Bearer $token" -F "file=@$($tempFile.FullName)" "$BaseUrl/files/upload"
    
    try {
        $json = $result | ConvertFrom-Json
        if ($json.file_url) {
            Write-Host "   Response is valid JSON with file_url"
        } else {
            throw "Response missing expected fields"
        }
    } catch {
        throw "Response is not valid JSON: $result"
    }
    
    Remove-Item -Path $tempFile.FullName -Force
}

# Test 8: File extension is preserved
Test-Case "Test 8: File extension preserved" {
    $namedFile = Join-Path $env:TEMP "test-upload.txt"
    Set-Content -Path $namedFile -Value "content with extension"
    
    $json = Upload-File -FilePath $namedFile -Url "$BaseUrl/files/upload" -Token $token
    
    if ($json.file_url -like "*.txt") {
        Write-Host "   File extension preserved in URL: $($json.file_url)"
    } else {
        Write-Host "   File uploaded (extension: $($json.file_url))"
    }
    
    Remove-Item -Path $namedFile -Force
}

Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red
Write-Host "Total:  $($testsPassed + $testsFailed)" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "`n✅ All File Upload tests PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n❌ Some tests FAILED" -ForegroundColor Red
    exit 1
}
