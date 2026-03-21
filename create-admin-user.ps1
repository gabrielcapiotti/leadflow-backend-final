#!/usr/bin/env pwsh
# Create admin user in PostgreSQL database

Write-Host "Creating ADMIN user in database..." -ForegroundColor Cyan

$dbHost = "localhost"
$dbPort = "2411"
$dbUser = "postgres"
$dbPassword = "venusia"
$dbName = "leadflow_test"
$adminEmail = "admin@leadflow.com"
$adminPassword = "Admin@Lead123"
# bcrypt hash of Admin@Lead123 with rounds=10
$bcryptHash = '$2a$10$SxXrCnqPXgqwhQ3J7rLr7Or0M3zLw9xJT2lppjGJI0LQMvzKnlkzW'

# SQL to create admin user
$sql = @"
BEGIN;

-- Delete if exists
DELETE FROM public.users WHERE email = '$adminEmail';

-- Get admin role id
WITH admin_role AS (
    SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN' LIMIT 1
)
-- Insert admin user
INSERT INTO public.users (
    id, name, email, password, role_id, 
    failed_attempts, lock_until, credentials_updated_at, 
    created_at, updated_at, deleted_at
)
SELECT
    gen_random_uuid(),
    'Admin User',
    '$adminEmail',
    '$bcryptHash',
    admin_role.id,
    0, NULL, CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
FROM admin_role
WHERE EXISTS (
    SELECT 1 FROM public.roles WHERE name = 'ROLE_ADMIN'
);

COMMIT;

-- Verify
SELECT email, role_id FROM public.users WHERE email = '$adminEmail';
"@

# Try to execute via psql
try {
    Write-Host "Attempting to create admin user via SQL..." -ForegroundColor Cyan
    
    $env:PGPASSWORD = $dbPassword
    
    $sqlFile = "create_admin.sql"
    $sql | Out-File -FilePath $sqlFile -Encoding ASCII
    
    $result = & psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $sqlFile 2>&1
    
    Remove-Item $sqlFile -Force
    
    Write-Host $result -ForegroundColor Green
    Write-Host "✅ Admin user created successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Email:    $adminEmail" -ForegroundColor Cyan
    Write-Host "Password: $adminPassword" -ForegroundColor Cyan
    
} catch {
    Write-Host "❌ Error: $_" -ForegroundColor Red
    exit 1
}
