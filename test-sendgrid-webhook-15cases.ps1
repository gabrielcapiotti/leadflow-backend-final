# Test Suite: SendGrid Webhook Endpoint (15 Cases)
# Endpoint: POST /webhooks/sendgrid
# Status: Production Ready
# Testing Strategy: curl.exe JSON payloads, no authentication required

param(
    [string]$BaseUrl = "http://localhost:8081/webhooks/sendgrid"
)

$ErrorActionPreference = "SilentlyContinue"
$testsPassed = 0
$testsFailed = 0
$timestamp = [int][double]::Parse((Get-Date -UFormat %s))

function Test-Case {
    param(
        [string]$Name,
        [string]$Payload,
        [int]$ExpectedStatus = 200
    )
    
    $response = curl.exe -s -w "`n%{http_code}" -X POST `
        -H "Content-Type: application/json" `
        -d $Payload `
        "$BaseUrl"
    
    $statusCode = $response[-1]
    $body = $response[0..($response.Length-2)] -join "`n"
    
    if ([int]$statusCode -eq $ExpectedStatus) {
        Write-Host "✅ $Name" -ForegroundColor Green
        $script:testsPassed++
    } else {
        Write-Host "❌ $Name (Expected $ExpectedStatus, got $statusCode)" -ForegroundColor Red
        Write-Host "   Response: $body" -ForegroundColor Red
        $script:testsFailed++
    }
}

Write-Host "`n=== SendGrid Webhook Endpoint Tests - 15 Cases ===" -ForegroundColor Cyan
Write-Host "Endpoint: $BaseUrl`n" -ForegroundColor Gray

# Test 1: Delivery event
$payload1 = ConvertTo-Json @(@{
    email = "delivered@example.com"
    event = "delivered"
    timestamp = $timestamp
    reason = "250 OK"
})
Test-Case "Test 1: Delivery event" $payload1

# Test 2: Open event
$payload2 = ConvertTo-Json @(@{
    email = "opened@example.com"
    event = "open"
    timestamp = $timestamp
    ip = "192.168.1.1"
    useragent = "Mozilla/5.0"
})
Test-Case "Test 2: Email opened event" $payload2

# Test 3: Click event
$payload3 = ConvertTo-Json @(@{
    email = "clicked@example.com"
    event = "click"
    timestamp = $timestamp
    url = "https://example.com"
    ip = "192.168.1.1"
})
Test-Case "Test 3: Email click event" $payload3

# Test 4: Bounce event (permanent)
$payload4 = ConvertTo-Json @(@{
    email = "permanent-bounce@example.com"
    event = "bounce"
    timestamp = $timestamp
    type = "permanent"
    reason = "550 User not found"
})
Test-Case "Test 4: Permanent bounce event" $payload4

# Test 5: Bounce event (temporary)
$payload5 = ConvertTo-Json @(@{
    email = "temp-bounce@example.com"
    event = "bounce"
    timestamp = $timestamp
    type = "temporary"
    reason = "421 Service temporarily unavailable"
})
Test-Case "Test 5: Temporary bounce event" $payload5

# Test 6: Blocked event
$payload6 = ConvertTo-Json @(@{
    email = "blocked@example.com"
    event = "blocked"
    timestamp = $timestamp
    reason = "Bounced Address"
})
Test-Case "Test 6: Email blocked event" $payload6

# Test 7: Spam report event
$payload7 = ConvertTo-Json @(@{
    email = "spammed@example.com"
    event = "spamreport"
    timestamp = $timestamp
    reason = "User marked email as spam"
})
Test-Case "Test 7: Spam report event" $payload7

# Test 8: Suppress event
$payload8 = ConvertTo-Json @(@{
    email = "suppressed@example.com"
    event = "suppress"
    timestamp = $timestamp
    reason = "Unsubscribe"
})
Test-Case "Test 8: Suppress/unsubscribe event" $payload8

# Test 9: Multiple events in batch (3 events)
$payload9 = ConvertTo-Json @(
    @{ email = "batch1@example.com"; event = "delivered"; timestamp = $timestamp },
    @{ email = "batch2@example.com"; event = "open"; timestamp = $timestamp },
    @{ email = "batch3@example.com"; event = "click"; timestamp = $timestamp }
)
Test-Case "Test 9: Multiple events batch (3 events)" $payload9

# Test 10: Mixed events batch with bounce
$payload10 = ConvertTo-Json @(
    @{ email = "mixed1@example.com"; event = "delivered"; timestamp = $timestamp },
    @{ email = "mixed2@example.com"; event = "bounce"; timestamp = $timestamp; type = "permanent" },
    @{ email = "mixed3@example.com"; event = "open"; timestamp = $timestamp },
    @{ email = "mixed4@example.com"; event = "spamreport"; timestamp = $timestamp }
)
Test-Case "Test 10: Mixed event types batch (4 events)" $payload10

# Test 11: Empty email field (should skip gracefully)
$payload11 = ConvertTo-Json @(@{
    email = ""
    event = "delivered"
    timestamp = $timestamp
})
Test-Case "Test 11: Empty email field (graceful skip)" $payload11

# Test 12: Missing event type (should skip gracefully)
$payload12 = ConvertTo-Json @(@{
    email = "noevent@example.com"
    timestamp = $timestamp
})
Test-Case "Test 12: Missing event type (graceful skip)" $payload12

# Test 13: Null values in optional fields
$payload13 = ConvertTo-Json @(@{
    email = "nullfields@example.com"
    event = "delivered"
    timestamp = $timestamp
    reason = $null
    ip = $null
})
Test-Case "Test 13: Null optional fields" $payload13

# Test 14: Large batch (10 events)
$bigBatch = @()
for ($i = 1; $i -le 10; $i++) {
    $bigBatch += @{
        email = "batch$i@example.com"
        event = if ($i % 3 -eq 0) { "bounce" } else { "delivered" }
        timestamp = $timestamp + $i
        type = if ($i % 3 -eq 0) { "permanent" } else { $null }
    }
}
$payload14 = ConvertTo-Json $bigBatch
Test-Case "Test 14: Large batch (10 events)" $payload14

# Test 15: Empty array (should succeed)
$payload15 = ConvertTo-Json @()
Test-Case "Test 15: Empty array payload" $payload15

Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red
Write-Host "Total:  $($testsPassed + $testsFailed)" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "`n✅ All SendGrid Webhook tests PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n❌ Some tests FAILED" -ForegroundColor Red
    exit 1
}
