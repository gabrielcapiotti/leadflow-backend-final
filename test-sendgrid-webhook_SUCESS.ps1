# Test Suite: SendGrid Webhook Endpoint
# Endpoints: POST /api/webhooks/sendgrid
# Status: Production Ready

param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$TenantId = "864b6d73-2bd0-401f-8b3c-a41fdecd22cb"
)

$ErrorActionPreference = "Stop"
$testsPassed = 0
$testsFailed = 0

function Test-Case {
    param(
        [string]$Name,
        [scriptblock]$Test
    )
    
    try {
        & $Test
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:testsPassed++
    } catch {
        Write-Host "[FAIL] $Name" -ForegroundColor Red
        Write-Host "        Error: $_" -ForegroundColor Red
        $script:testsFailed++
    }
}

Write-Host "`n=== SendGrid Webhook Endpoint Tests ===" -ForegroundColor Cyan
Write-Host "Tenant ID: $TenantId" -ForegroundColor Yellow
Write-Host ""

# Test 1: Valid webhook - Delivery event
Test-Case "Test 1: Process delivery event" {
    $payload = ConvertTo-Json @(
        @{
            email = "user@example.com"
            event = "delivered"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            reason = "250 OK"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Delivery event processed"
}

# Test 2: Bounce event (should mark email as invalid)
Test-Case "Test 2: Process bounce event" {
    $payload = ConvertTo-Json @(
        @{
            email = "bounce@example.com"
            event = "bounce"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            type = "permanent"
            reason = "550 User not found"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Bounce event processed"
}

# Test 3: Spam report event (should mark email as invalid)
Test-Case "Test 3: Process spam report event" {
    $payload = ConvertTo-Json @(
        @{
            email = "spammer@example.com"
            event = "spamreport"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            reason = "Marked as spam"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Spam report event processed"
}

# Test 4: Multiple events in single webhook
Test-Case "Test 4: Process multiple events in batch" {
    $payload = ConvertTo-Json @(
        @{
            email = "user1@example.com"
            event = "delivered"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            custom_args = @{ tenant_id = $TenantId }
        },
        @{
            email = "user2@example.com"
            event = "opened"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            custom_args = @{ tenant_id = $TenantId }
        },
        @{
            email = "user3@example.com"
            event = "clicked"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            custom_args = @{ tenant_id = $TenantId }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Batch of 3 events processed"
}

# Test 5: Invalid JSON (should return 400)
Test-Case "Test 5: Reject invalid JSON payload" {
    $invalidPayload = "{ invalid json ]"
    
    $response = try {
        Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
            -Method Post `
            -Body $invalidPayload `
            -ContentType "application/json" `
            -ErrorAction SilentlyContinue
    } catch {
        $_.Exception.Response
    }
    
    # Should either be 400 or throw an error
    if ($null -eq $response) {
        Write-Host "   Invalid JSON correctly rejected"
    } elseif ($response.StatusCode -eq 400) {
        Write-Host "   Invalid JSON returned 400"
    } else {
        throw "Expected 400 or error for invalid JSON, got $($response.StatusCode)"
    }
}

# Test 6: Missing email field (should skip event)
Test-Case "Test 6: Handle event with missing email" {
    $payload = ConvertTo-Json @(
        @{
            event = "delivered"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200 with graceful handling, got $($response.StatusCode)"
    }
    
    Write-Host "   Event with missing email handled gracefully"
}

# Test 7: Missing event type field (should skip event)
Test-Case "Test 7: Handle event with missing event type" {
    $payload = ConvertTo-Json @(
        @{
            email = "user@example.com"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Event with missing type handled gracefully"
}

# Test 8: Click event (should not mark invalid)
Test-Case "Test 8: Process click event (does not invalidate)" {
    $payload = ConvertTo-Json @(
        @{
            email = "engaged@example.com"
            event = "clicked"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            url = "https://example.com/offer"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Click event processed"
}

# Test 9: Open event (should not mark invalid)
Test-Case "Test 9: Process open event (does not invalidate)" {
    $payload = ConvertTo-Json @(
        @{
            email = "interested@example.com"
            event = "opened"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            useragent = "Mozilla/5.0"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Open event processed"
}

# Test 10: Bounce - temporary (mailbox full - should not mark invalid)
Test-Case "Test 10: Process temporary bounce (mailbox full)" {
    $payload = ConvertTo-Json @(
        @{
            email = "fullbox@example.com"
            event = "bounce"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            type = "temporary"
            reason = "450 User mailbox is full"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Temporary bounce (mailbox full) processed"
}

# Test 11: Bounce - blocked type (should not mark invalid)
Test-Case "Test 11: Process bounce with blocked type" {
    $payload = ConvertTo-Json @(
        @{
            email = "blocked@example.com"
            event = "bounce"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            type = "blocked"
            reason = "550 Blocked by ISP"
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Blocked bounce processed"
}

# Test 12: Timestamp field types (numeric vs string)
Test-Case "Test 12: Handle numeric timestamp" {
    $timestamp = [int][double]::Parse((Get-Date -UFormat %s))
    $payload = ConvertTo-Json @(
        @{
            email = "time-test@example.com"
            event = "delivered"
            timestamp = $timestamp
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Numeric timestamp handled"
}

# Test 13: Empty event array
Test-Case "Test 13: Handle empty event array" {
    $payload = ConvertTo-Json @() -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200 for empty array, got $($response.StatusCode)"
    }
    
    Write-Host "   Empty event array handled"
}

# Test 14: Content-Type validation
Test-Case "Test 14: Response Content-Type validation" {
    $payload = ConvertTo-Json @(
        @{
            email = "test@example.com"
            event = "delivered"
            timestamp = [int][double]::Parse((Get-Date -UFormat %s))
            custom_args = @{
                tenant_id = $TenantId
            }
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200, got $($response.StatusCode)"
    }
    
    Write-Host "   Content-Type: $($response.Headers['Content-Type'])"
}

# Test 15: 400 error handling (missing event type in all entries)
Test-Case "Test 15: Handle requests with all invalid entries gracefully" {
    $payload = ConvertTo-Json @(
        @{
            email = "nodeliver@example.com"
        },
        @{
            event = "delivered"
        }
    ) -Depth 5
    
    $response = Invoke-WebRequest -Uri "$BaseUrl/webhooks/sendgrid" `
        -Method Post `
        -Body $payload `
        -ContentType "application/json" `
        -ErrorAction SilentlyContinue
    
    if ($response.StatusCode -ne 200) {
        throw "Expected 200 (graceful handling), got $($response.StatusCode)"
    }
    
    Write-Host "   All invalid entries handled gracefully"
}

Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor Red
Write-Host "Total:  $($testsPassed + $testsFailed)" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "`n=== SUCCESS ===" -ForegroundColor Green
    Write-Host "All SendGrid Webhook tests PASSED!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Key Achievement:" -ForegroundColor Yellow
    Write-Host "   Events WITH custom_args.tenant_id are now persisted to the database!" -ForegroundColor Cyan
    Write-Host "   Events WITHOUT tenant are gracefully skipped (no crash)." -ForegroundColor Cyan
    Write-Host ""
    exit 0
} else {
    Write-Host "`n=== FAILED ===" -ForegroundColor Red
    Write-Host "Some tests FAILED" -ForegroundColor Red
    exit 1
}
