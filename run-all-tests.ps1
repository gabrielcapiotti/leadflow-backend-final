############################################################
# EXECUTE ALL TEST SUITES - COMPLETE VALIDATION
############################################################

$TestFiles = @(
    "Test-Auth-Fixed_SUCESS.ps1",
    "test-roles-management_SUCESS.ps1",
    "test-billing_SUCESS.ps1",
    "test-webhooks-Oficial_SUCESS.ps1",
    "test-webhook-management_SUCESS.ps1",
    "test-dashboard-analytics_SUCESS.ps1",
    "test-users-management_SUCESS.ps1",
    "test-leads-all-Oficial_SUCESS.ps1",
    "test-leads-vendorleads_SUCESS.ps1",
    "test-audit-logs_SUCESS.ps1",
    "test-auth-sessions_SUCESS.ps1",
    "test-vendors-Oficial_SUCESS.ps1",
    "test-admin-tranche3_SUCESS.ps1",
    "test-all-admin-endpoints_SUCESS.ps1",
    "test-ai-endpoints-MOCK_SUCESS.ps1"
)

$Results = @()
$TotalPassed = 0
$TotalFailed = 0
$StartTime = Get-Date

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  LEADFLOW BACKEND - COMPLETE TEST EXECUTION" -ForegroundColor Cyan
Write-Host "  Starting: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan

foreach ($testFile in $TestFiles) {
    $testName = $testFile -replace "_SUCESS.ps1", "" -replace "test-", "" -replace "Test-", ""
    Write-Host "[TEST] $testName" -ForegroundColor Yellow
    
    $output = & powershell -ExecutionPolicy Bypass -File $testFile 2>&1 | Out-String
    
    # Extract pass/fail counts
    if ($output -match "Passed:\s*(\d+)") {
        $passed = [int]$matches[1]
    } else {
        $passed = 0
    }
    
    if ($output -match "Failed:\s*(\d+)") {
        $failed = [int]$matches[1]
    } else {
        $failed = 0
    }
    
    $status = if ($failed -eq 0 -and $passed -gt 0) { "PASS" } else { "FAIL" }
    $color = if ($failed -eq 0 -and $passed -gt 0) { "Green" } else { "Red" }
    
    Write-Host "  Result: $status ($passed passed, $failed failed)" -ForegroundColor $color
    
    $Results += [PSCustomObject]@{
        TestName = $testName
        Passed = $passed
        Failed = $failed
        Status = $status
    }
    
    $TotalPassed += $passed
    $TotalFailed += $failed
}

$EndTime = Get-Date
$Duration = ($EndTime - $StartTime).TotalSeconds

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  FINAL REPORT" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$Results | Format-Table -AutoSize -Property @(
    @{Label="Test Suite"; Expression={$_.TestName}},
    @{Label="Passed"; Expression={$_.Passed}; Alignment="Right"},
    @{Label="Failed"; Expression={$_.Failed}; Alignment="Right"},
    @{Label="Status"; Expression={$_.Status}}
)

Write-Host ""
Write-Host "SUMMARY:" -ForegroundColor Cyan
Write-Host "  Total Passed: $TotalPassed" -ForegroundColor Green
Write-Host "  Total Failed: $TotalFailed" -ForegroundColor Red
Write-Host "  Execution Time: $([math]::Round($Duration, 2)) seconds" -ForegroundColor Cyan

$PassRate = if (($TotalPassed + $TotalFailed) -gt 0) { 
    [math]::Round(($TotalPassed / ($TotalPassed + $TotalFailed)) * 100, 1) 
} else { 
    0 
}

Write-Host "  Pass Rate: $PassRate%" -ForegroundColor (if ($PassRate -eq 100) { "Green" } else { "Yellow" })
Write-Host ""
Write-Host "============================================================`n" -ForegroundColor Cyan
