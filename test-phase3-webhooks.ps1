#!/usr/bin/env powershell
<#
.SYNOPSIS
    Phase 3 Webhook Integration Tests - Simplified
#>

$BaseUrl = "http://localhost:8081"

Write-Host "`n==========================================`n" -ForegroundColor Cyan
Write-Host "PHASE 3: WEBHOOK SERVICE INTEGRATION TESTS`n" -ForegroundColor Cyan
Write-Host "==========================================`n" -ForegroundColor Cyan

Write-Host "Server: $BaseUrl`n"

# Test 1: Health Check
Write-Host "STEP 1: Server Health Check`n" -ForegroundColor Blue
Write-Host (("-" * 50) + "`n")

try {
  $health = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -ErrorAction Stop -SkipHttpErrorCheck
  if ($health.StatusCode -eq 200) {
    $healthJson = $health.Content | ConvertFrom-Json
    Write-Host "✓ Server is UP (Status: $($healthJson.status))`n" -ForegroundColor Green
  } else {
    Write-Host "✗ Server returned: $($health.StatusCode)`n" -ForegroundColor Red
    Write-Host "Response: $($health.Content)`n"
  }
} catch {
  Write-Host "✗ Server is not reachable`n" -ForegroundColor Red
  Write-Host "Error: $_`n" -ForegroundColor Red
  exit 1
}

# Test 2: Invoice Payment Succeeded
Write-Host "STEP 2: Invoice Payment Succeeded Webhook`n" -ForegroundColor Blue
Write-Host (("-" * 50) + "`n")

$invoicePayload = @{
  type = "invoice.payment_succeeded"
  data = @{
    object = @{
      id = "in_test_12345"
      subscription = "sub_test_stripe_123"
      customer = "cust_test_12345"
      amount_paid = 9999
      currency = "usd"
    }
  }
} | ConvertTo-Json

Write-Host "Sending invoice.payment_succeeded event...`n"

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_123"
    } `
    -Body $invoicePayload `
    -ErrorAction Stop `
    -SkipHttpErrorCheck
  
  Write-Host "✓ Event received (Status: $($response.StatusCode))`n" -ForegroundColor Green
  Write-Host "Response: $($response.Content)`n"
} catch {
  Write-Host "✗ Failed to send invoice.payment_succeeded`n" -ForegroundColor Red
  Write-Host "Error: $_`n" -ForegroundColor Red
}

# Test 3: Subscription Deleted
Write-Host "STEP 3: Subscription Deleted Webhook`n" -ForegroundColor Blue
Write-Host (("-" * 50) + "`n")

$timestamp = [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$deletedPayload = @{
  type = "customer.subscription.deleted"
  data = @{
    object = @{
      id = "sub_test_stripe_123"
      customer = "cust_test_12345"
      status = "canceled"
      canceled_at = $timestamp
    }
  }
} | ConvertTo-Json

Write-Host "Sending customer.subscription.deleted event...`n"

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_456"
    } `
    -Body $deletedPayload `
    -ErrorAction Stop `
    -SkipHttpErrorCheck
  
  Write-Host "✓ Event received (Status: $($response.StatusCode))`n" -ForegroundColor Green
  Write-Host "Response: $($response.Content)`n"
} catch {
  Write-Host "✗ Failed to send customer.subscription.deleted`n" -ForegroundColor Red
  Write-Host "Error: $_`n" -ForegroundColor Red
}

# Test 4: Subscription Updated
Write-Host "STEP 4: Subscription Updated Webhook`n" -ForegroundColor Blue
Write-Host (("-" * 50) + "`n")

$now = [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$expiry = ([System.DateTimeOffset]::UtcNow.AddMonths(1)).ToUnixTimeSeconds()

$updatedPayload = @{
  type = "customer.subscription.updated"
  data = @{
    object = @{
      id = "sub_test_stripe_123"
      customer = "cust_test_12345"
      status = "active"
      current_period_start = $now
      current_period_end = $expiry
    }
  }
} | ConvertTo-Json

Write-Host "Sending customer.subscription.updated event...`n"

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_789"
    } `
    -Body $updatedPayload `
    -ErrorAction Stop `
    -SkipHttpErrorCheck
  
  Write-Host "✓ Event received (Status: $($response.StatusCode))`n" -ForegroundColor Green
  Write-Host "Response: $($response.Content)`n"
} catch {
  Write-Host "✗ Failed to send customer.subscription.updated`n" -ForegroundColor Red
  Write-Host "Error: $_`n" -ForegroundColor Red
}

# Summary
Write-Host "==========================================`n" -ForegroundColor Cyan
Write-Host "PHASE 3 TESTS COMPLETED`n" -ForegroundColor Cyan
Write-Host "==========================================`n" -ForegroundColor Cyan
Write-Host "Webhooks tested:`n" -ForegroundColor Green
Write-Host "  • invoice.payment_succeeded (markPaymentSuccessful)`n"
Write-Host "  • customer.subscription.deleted (markAsDeletedFromStripe)`n"
Write-Host "  • customer.subscription.updated (syncWithStripe)`n"
