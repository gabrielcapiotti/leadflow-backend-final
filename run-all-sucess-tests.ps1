# ============================================================
# RUN ALL SUCESS TESTS SEQUENTIALLY
# ============================================================

$tests = @(
    "Test-Auth-Fixed_SUCESS.ps1",
    "setup-webhook-mappings-direct_SUCESS.ps1",
    "test-all-admin-endpoints_SUCESS.ps1",
    "test-billing_SUCESS.ps1",
    "test-ai-endpoints-MOCK_SUCESS.ps1",
    "test-all-Settings-Oficial-SUCESS.ps1",
    "test-webhooks-Oficial_SUCESS.ps1",
    "test-vendors-Oficial_SUCESS.ps1",
    "leads-all-Oficial_SUCESS.ps1",
    "test-leads-vendorleads_SUCESS.ps1",
    "test-admin-tranche3_SUCESS.ps1"
)

$results = @()
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  SEQUENTIAL SUCESS TEST SUITE EXECUTION" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Timestamp: $timestamp" -ForegroundColor White
Write-Host "Total tests: $($tests.Count)" -ForegroundColor White
Write-Host ""

$passed = 0
$failed = 0
$skipped = 0

foreach ($i in 1..$tests.Count) {
    $testName = $tests[$i - 1]
    $testPath = ".\$testName"
    
    Write-Host "[$i/$($tests.Count)] Running: $testName" -ForegroundColor Yellow
    Write-Host "   " -NoNewline
    
    try {
        if (-not (Test-Path $testPath)) {
            Write-Host "SKIPPED (file not found)" -ForegroundColor Gray
            $results += @{
                Test = $testName
                Status = "SKIPPED"
                Reason = "File not found"
                Time = Get-Date -Format "HH:mm:ss"
            }
            $skipped++
        } else {
            $startTime = Get-Date
            $output = & powershell -ExecutionPolicy Bypass -File $testPath 2>&1
            $duration = $(Get-Date) - $startTime
            
            # Check for success indicators in output
            if ($output -match "SUCCESS|PASSED|Pass Rate: 100%|All tests passed|OK" -and 
                $output -notmatch "FAILED|FAILED:|Failed:|ERROR") {
                Write-Host "PASSED" -ForegroundColor Green
                $results += @{
                    Test = $testName
                    Status = "PASSED"
                    Duration = "$($duration.TotalSeconds)s"
                    Time = Get-Date -Format "HH:mm:ss"
                }
                $passed++
            } else {
                Write-Host "FAILED" -ForegroundColor Red
                $results += @{
                    Test = $testName
                    Status = "FAILED"
                    Duration = "$($duration.TotalSeconds)s"
                    Time = Get-Date -Format "HH:mm:ss"
                }
                $failed++
            }
        }
    } catch {
        Write-Host "ERROR" -ForegroundColor Red
        Write-Host "   $_" -ForegroundColor Red
        $results += @{
            Test = $testName
            Status = "ERROR"
            Reason = $_.Exception.Message
            Time = Get-Date -Format "HH:mm:ss"
        }
        $failed++
    }
    
    # Wait between tests
    Start-Sleep -Seconds 2
}

# Summary
Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  TEST EXECUTION SUMMARY" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$results | ForEach-Object {
    $statusColor = switch ($_.Status) {
        "PASSED" { "Green" }
        "FAILED" { "Red" }
        "ERROR" { "Red" }
        "SKIPPED" { "Gray" }
    }
    Write-Host "$($_.Test): " -NoNewline
    Write-Host $_.Status -ForegroundColor $statusColor -NoNewline
    if ($_.Duration) {
        Write-Host " ($($_.Duration))" -ForegroundColor White
    } else {
        Write-Host ""
    }
}

Write-Host ""
Write-Host "Results Summary:" -ForegroundColor White
Write-Host "  Passed: $passed" -ForegroundColor Green
Write-Host "  Failed: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "  Skipped: $skipped" -ForegroundColor Gray
Write-Host "  Total: $($tests.Count)" -ForegroundColor White

$passRate = if ($tests.Count -gt 0) { [math]::Round(($passed / ($tests.Count - $skipped)) * 100, 2) } else { 0 }
Write-Host "  Pass Rate: $passRate%" -ForegroundColor $(if ($passRate -eq 100) { "Green" } else { "Yellow" })

Write-Host "`n============================================================" -ForegroundColor Cyan

if ($failed -eq 0 -and $skipped -lt $tests.Count) {
    Write-Host "[PASS] ALL TESTS PASSED!" -ForegroundColor Green
    exit 0
} elseif ($failed -gt 0) {
    Write-Host "[FAIL] SOME TESTS FAILED" -ForegroundColor Red
    exit 1
} else {
    Write-Host "[WARN] NO TESTS EXECUTED" -ForegroundColor Yellow
    exit 2
}
