#!/usr/bin/env pwsh

# LEADFLOW DATABASE EXPLORER
# Interactive tool to understand database structure

param(
    [string]$action = "menu"
)

$green = 'Green'
$cyan = 'Cyan'
$yellow = 'Yellow'
$white = 'White'

$baseUrl = "http://localhost:8081"
$dbhost = "localhost"
$dbport = "2411"
$dbuser = "postgres"
$dbpass = "venusia"
$dbname = "leadflow_test"

# Store connection details
$env:PGPASSWORD = $dbpass

function Show-Menu {
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor $cyan
    Write-Host "║     LEADFLOW DATABASE EXPLORER                           ║" -ForegroundColor $cyan
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor $cyan
    Write-Host ""
    Write-Host "SELECT AN OPTION:" -ForegroundColor $yellow
    Write-Host ""
    Write-Host "  [1] 📊 Show ALL Tables & Row Counts" -ForegroundColor $white
    Write-Host "  [2] 👥 Users & Authentication Tables" -ForegroundColor $white
    Write-Host "  [3] 💼 Vendor & Lead Management Tables" -ForegroundColor $white
    Write-Host "  [4] 💳 Billing & Subscription Tables" -ForegroundColor $white
    Write-Host "  [5] 📝 Audit & Logging Tables" -ForegroundColor $white
    Write-Host "  [6] 🔗 View Table Relationships" -ForegroundColor $white
    Write-Host "  [7] 🔍 Query Table Details" -ForegroundColor $white
    Write-Host "  [8] 📈 Database Statistics" -ForegroundColor $white
    Write-Host "  [0] Exit" -ForegroundColor $white
    Write-Host ""
}

function Get-TableCount {
    param([string]$tableName)
    try {
        $result = & psql -h $dbhost -p $dbport -U $dbuser -d $dbname -t -c "SELECT COUNT(*) FROM public.$tableName;" 2>&1
        return $result.Trim()
    }
    catch {
        return "N/A"
    }
}

function Show-All-Tables {
    Write-Host "`n📋 ALL TABLES (37 total)" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    $tables = @(
        'audit_logs', 'email_events', 'failed_webhook_events', 'flyway_schema_history',
        'lead_status_history', 'leads', 'login_audit', 'logs', 'password_reset_token',
        'payment_checkout_requests', 'payment_events', 'payments', 'plans', 'refresh_tokens',
        'roles', 'security_audit_logs', 'settings', 'stripe_event_logs', 'subscription_audits',
        'subscription_history', 'subscriptions', 'system_audit_logs', 'tenants', 'usage_limits',
        'user_sessions', 'users', 'vendor_audit_logs', 'vendor_features', 'vendor_lead_alerts',
        'vendor_lead_conversations', 'vendor_lead_messages', 'vendor_lead_stage_history', 'vendor_leads',
        'vendor_risk_alerts', 'vendor_usage', 'vendors', 'webhook_events'
    )
    
    $totalRows = 0
    
    foreach ($table in $tables) {
        $count = Get-TableCount $table
        if ($count -match '^\d+$') {
            $totalRows += $count
            if ([int]$count -gt 0) {
                Write-Host "  ✓ $table : $count rows" -ForegroundColor $green
            } else {
                Write-Host "  ○ $table : $count rows" -ForegroundColor $gray
            }
        }
    }
    
    Write-Host ""
    Write-Host "  Total Rows Across All Tables: $totalRows" -ForegroundColor $cyan
    Write-Host ""
}

function Show-Auth-Tables {
    Write-Host "`n👥 AUTHENTICATION & AUTHORIZATION" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    $tables = @('users', 'roles', 'user_sessions', 'refresh_tokens', 'password_reset_token', 'login_audit')
    
    foreach ($table in $tables) {
        $count = Get-TableCount $table
        Write-Host "  ✓ $table`t: $count rows" -ForegroundColor $white
    }
    
    Write-Host ""
    Write-Host "Purpose:" -ForegroundColor $yellow
    Write-Host "  • users: User accounts with BCrypt password hashing"
    Write-Host "  • roles: Role definitions (ROLE_ADMIN, ROLE_USER)"
    Write-Host "  • user_sessions: Active logged-in sessions"
    Write-Host "  • refresh_tokens: JWT token refresh mechanism"
    Write-Host "  • login_audit: Login attempt tracking for security"
    Write-Host ""
}

function Show-Vendor-Lead-Tables {
    Write-Host "`n💼 VENDOR & LEAD MANAGEMENT" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    $tables = @('vendors', 'vendor_features', 'leads', 'vendor_leads', 'vendor_lead_conversations', 'lead_status_history', 'vendor_lead_alerts')
    
    foreach ($table in $tables) {
        $count = Get-TableCount $table
        Write-Host "  ✓ $table`t: $count rows" -ForegroundColor $white
    }
    
    Write-Host ""
    Write-Host "Purpose:" -ForegroundColor $yellow
    Write-Host "  • vendors: Partner/vendor company profiles"
    Write-Host "  • vendor_features: Feature access control per vendor"
    Write-Host "  • leads: Lead records from campaigns"
    Write-Host "  • vendor_leads: Vendor-to-lead assignments"
    Write-Host "  • vendor_lead_conversations: Vendor-lead messaging"
    Write-Host "  • lead_status_history: Lead progression audit trail"
    Write-Host ""
}

function Show-Billing-Tables {
    Write-Host "`n💳 BILLING & SUBSCRIPTIONS" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    $tables = @('plans', 'subscriptions', 'subscription_history', 'payments', 'payment_events', 'payment_checkout_requests', 'usage_limits')
    
    foreach ($table in $tables) {
        $count = Get-TableCount $table
        Write-Host "  ✓ $table`t: $count rows" -ForegroundColor $white
    }
    
    Write-Host ""
    Write-Host "Purpose:" -ForegroundColor $yellow
    Write-Host "  • plans: Available subscription plans"
    Write-Host "  • subscriptions: Active vendor subscriptions (Stripe-integrated)"
    Write-Host "  • payments: Payment transaction records"
    Write-Host "  • usage_limits: Per-plan resource limits"
    Write-Host ""
}

function Show-Audit-Tables {
    Write-Host "`n📝 AUDIT & SECURITY LOGGING" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    $tables = @('audit_logs', 'login_audit', 'security_audit_logs', 'system_audit_logs', 'vendor_audit_logs', 'logs')
    
    foreach ($table in $tables) {
        $count = Get-TableCount $table
        Write-Host "  ✓ $table`t: $count rows" -ForegroundColor $white
    }
    
    Write-Host ""
    Write-Host "Purpose:" -ForegroundColor $yellow
    Write-Host "  • login_audit: All login attempts (success/failure)"
    Write-Host "  • security_audit_logs: Security-sensitive events (role changes, etc.)"
    Write-Host "  • audit_logs: Generic operational audit trail"
    Write-Host "  • Enables complete system auditability & compliance"
    Write-Host ""
}

function Show-Relationships {
    Write-Host "`n🔗 TABLE RELATIONSHIPS & FOREIGN KEYS" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    Write-Host ""
    Write-Host "USERS" -ForegroundColor $yellow
    Write-Host "  ├─ role_id → ROLES.id" -ForegroundColor $white
    Write-Host "  ├─ Referenced by: USER_SESSIONS, REFRESH_TOKENS, LOGIN_AUDIT" -ForegroundColor $white
    Write-Host ""
    
    Write-Host "VENDORS" -ForegroundColor $yellow
    Write-Host "  ├─ user_id → USERS.id" -ForegroundColor $white
    Write-Host "  ├─ Has many: VENDOR_LEADS, SUBSCRIPTIONS, PAYMENTS, WEBHOOK_EVENTS" -ForegroundColor $white
    Write-Host ""
    
    Write-Host "VENDOR_LEADS" -ForegroundColor $yellow
    Write-Host "  ├─ vendor_id → VENDORS.id" -ForegroundColor $white
    Write-Host "  ├─ lead_id → LEADS.id" -ForegroundColor $white
    Write-Host "  ├─ Has many: VENDOR_LEAD_CONVERSATIONS, VENDOR_LEAD_MESSAGES, VENDOR_LEAD_ALERTS" -ForegroundColor $white
    Write-Host ""
    
    Write-Host "SUBSCRIPTIONS" -ForegroundColor $yellow
    Write-Host "  ├─ vendor_id → VENDORS.id" -ForegroundColor $white
    Write-Host "  ├─ plan_id → PLANS.id" -ForegroundColor $white
    Write-Host "  ├─ Has: SUBSCRIPTION_HISTORY, SUBSCRIPTION_AUDITS" -ForegroundColor $white
    Write-Host ""
}

function Show-Statistics {
    Write-Host "`n📈 DATABASE STATISTICS" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    
    # User statistics
    $userCount = Get-TableCount 'users'
    $adminCount = & psql -h $dbhost -p $dbport -U $dbuser -d $dbname -t -c "SELECT COUNT(*) FROM users WHERE role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN');" 2>&1 | ForEach-Object {$_.Trim()}
    
    Write-Host ""
    Write-Host "Users:" -ForegroundColor $yellow
    Write-Host "  Total Users: $userCount" -ForegroundColor $white
    Write-Host "  Admin Users: $adminCount" -ForegroundColor $white
    
    # Vendor statistics
    $vendorCount = Get-TableCount 'vendors'
    Write-Host ""
    Write-Host "Vendors:" -ForegroundColor $yellow
    Write-Host "  Total Vendors: $vendorCount" -ForegroundColor $white
    
    # Lead statistics
    $leadCount = Get-TableCount 'leads'
    $vendorLeadCount = Get-TableCount 'vendor_leads'
    Write-Host ""
    Write-Host "Leads:" -ForegroundColor $yellow
    Write-Host "  Total Leads: $leadCount" -ForegroundColor $white
    Write-Host "  Vendor-Lead Assignments: $vendorLeadCount" -ForegroundColor $white
    
    # Billing statistics
    $subscriptionCount = Get-TableCount 'subscriptions'
    $activeSubscriptions = & psql -h $dbhost -p $dbport -U $dbuser -d $dbname -t -c "SELECT COUNT(*) FROM subscriptions WHERE status = 'active';" 2>&1 | ForEach-Object {$_.Trim()}
    Write-Host ""
    Write-Host "Billing:" -ForegroundColor $yellow
    Write-Host "  Total Subscriptions: $subscriptionCount" -ForegroundColor $white
    Write-Host "  Active Subscriptions: $activeSubscriptions" -ForegroundColor $white
    
    Write-Host ""
}

function Query-TableDetails {
    Write-Host ""
    $table = Read-Host "Enter table name (or press Enter to cancel)"
    
    if ([string]::IsNullOrWhiteSpace($table)) {
        return
    }
    
    Write-Host ""
    Write-Host "Schema for table: $table" -ForegroundColor $cyan
    Write-Host "=" * 60 -ForegroundColor $gray
    & psql -h $dbhost -p $dbport -U $dbuser -d $dbname -c "\d $table" 2>&1 | Select-Object -First 50
    Write-Host ""
}

# Main loop
do {
    Show-Menu
    $choice = Read-Host "Enter your choice"
    
    switch($choice) {
        "1" { Show-All-Tables }
        "2" { Show-Auth-Tables }
        "3" { Show-Vendor-Lead-Tables }
        "4" { Show-Billing-Tables }
        "5" { Show-Audit-Tables }
        "6" { Show-Relationships }
        "7" { Query-TableDetails }
        "8" { Show-Statistics }
        "0" { Write-Host "`nGoodbye!`n" -ForegroundColor $green; exit 0 }
        default { Write-Host "`n❌ Invalid choice. Try again.`n" -ForegroundColor Red }
    }
} while ($true)
