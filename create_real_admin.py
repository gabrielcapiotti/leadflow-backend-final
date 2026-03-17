#!/usr/bin/env python3
"""
Create a real ROLE_ADMIN user with bcrypt hashing
Uses bcrypt strength 10 (same as application)
"""
import bcrypt
import subprocess
import sys
import os

def create_admin_user():
    # Credentials
    email = "admin@leadflow.com"
    password = "Admin@Lead123"
    name = "Admin User"
    
    # Generate bcrypt hash with strength 10
    salt = bcrypt.gensalt(rounds=10)
    password_hash = bcrypt.hashpw(password.encode('utf-8'), salt).decode('utf-8')
    
    print(f"📝 Creating ROLE_ADMIN user")
    print(f"  Email: {email}")
    print(f"  Password: {password}")
    print(f"  Hash: {password_hash}")
    print()
    
    # Remove old users with same email
    remove_sql = f"DELETE FROM public.users WHERE email = '{email}';"
    
    # Create new admin user
    create_sql = f"""
WITH admin_role AS (
    SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN' LIMIT 1
)
INSERT INTO public.users (
    id, name, email, password, role_id, 
    failed_attempts, lock_until, credentials_updated_at, 
    created_at, updated_at, deleted_at
)
SELECT
    gen_random_uuid(),
    '{name}',
    '{email}',
    '{password_hash}',
    admin_role.id,
    0, NULL, CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
FROM admin_role;
"""
    
    try:
        # Execute psql
        env = os.environ.copy()
        env['PGPASSWORD'] = 'venusia'
        
        # Remove old user
        result = subprocess.run(
            ['psql', '-h', 'localhost', '-p', '2411', '-U', 'postgres', '-d', 'leadflow_test', '-c', remove_sql],
            env=env,
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode != 0:
            print(f"⚠️  Warning removing old user: {result.stderr}")
        
        # Create new user
        result = subprocess.run(
            ['psql', '-h', 'localhost', '-p', '2411', '-U', 'postgres', '-d', 'leadflow_test', '-c', create_sql],
            env=env,
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode == 0:
            print("✅ User created in database")
            print()
            print("=" * 60)
            print("ADMIN USER CREATED SUCCESSFULLY")
            print("=" * 60)
            print(f"Email:    {email}")
            print(f"Password: {password}")
            print("=" * 60)
            return True
        else:
            print(f"❌ Error creating user: {result.stderr}")
            return False
            
    except Exception as e:
        print(f"❌ Error: {e}")
        return False

if __name__ == '__main__':
    success = create_admin_user()
    sys.exit(0 if success else 1)
