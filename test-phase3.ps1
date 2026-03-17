param()

$BaseUrl = "http://localhost:8081"

Write-Host "=========================================="
Write-Host "PHASE 3: WEBHOOK SERVICE INTEGRATION TESTS"
Write-Host "=========================================="
Write-Host "Server: $BaseUrl"
Write-Host ""

# Test 1: Health Check
Write-Host "STEP 1: Server Health Check" -ForegroundColor Blue
Write-Host "===================="

try {
  $health = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -ErrorAction Stop -SkipHttpErrorCheck
  if ($health.StatusCode -eq 200) {
    $healthJson = $health.Content | ConvertFrom-Json
    Write-Host "[OK] Server is UP (Status: $($healthJson.status))" -ForegroundColor Green
  } else {
    Write-Host "[WARN] Server returned: $($health.StatusCode)" -ForegroundColor Yellow
  }
} catch {
  Write-Host "[ERROR] Server is not reachable" -ForegroundColor Red
  exit 1
}

Write-Host ""

# Test 2: Invoice Payment Succeeded
Write-Host "STEP 2: Invoice Payment Succeeded" -ForegroundColor Blue
Write-Host "===================="

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

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_123"
    } `
    -Body $invoicePayload `
    -SkipHttpErrorCheck
  
  Write-Host "[OK] Event received (Status: $($response.StatusCode))" -ForegroundColor Green
} catch {
  Write-Host "[ERROR] Failed: $_" -ForegroundColor Red
}

Write-Host ""

# Test 3: Subscription Deleted
Write-Host "STEP 3: Subscription Deleted" -ForegroundColor Blue
Write-Host "===================="

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

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_456"
    } `
    -Body $deletedPayload `
    -SkipHttpErrorCheck
  
  Write-Host "[OK] Event received (Status: $($response.StatusCode))" -ForegroundColor Green
} catch {
  Write-Host "[ERROR] Failed: $_" -ForegroundColor Red
}

Write-Host ""

# Test 4: Subscription Updated
Write-Host "STEP 4: Subscription Updated" -ForegroundColor Blue
Write-Host "===================="

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

try {
  $response = Invoke-WebRequest -Uri "$BaseUrl/webhook" `
    -Method POST `
    -Headers @{
      "Content-Type" = "application/json"
      "Stripe-Signature" = "test_signature_789"
    } `
    -Body $updatedPayload `
    -SkipHttpErrorCheck
  
  Write-Host "[OK] Event received (Status: $($response.StatusCode))" -ForegroundColor Green
} catch {
  Write-Host "[ERROR] Failed: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=========================================="
Write-Host "PHASE 3 TESTS COMPLETED" -ForegroundColor Cyan
Write-Host "=========================================="
