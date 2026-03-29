-- Add code column to plans table for stable plan identification
-- Allows using plan codes (e.g., "STANDARD", "PREMIUM") instead of plan names
-- This aligns with Stripe's price_id pattern and improves plan management

ALTER TABLE plans ADD COLUMN code VARCHAR(50);

-- Populate code for existing plans based on their names
UPDATE plans SET code = 'STANDARD' WHERE name = 'Leadflow Standard';
UPDATE plans SET code = 'PREMIUM' WHERE name = 'Leadflow Premium';
UPDATE plans SET code = 'ENTERPRISE' WHERE name = 'Leadflow Enterprise';

-- For any other plans, use a slug version of the name
UPDATE plans 
SET code = LOWER(REPLACE(REPLACE(name, ' ', '_'), '-', '_'))
WHERE code IS NULL;

-- Add unique constraint
ALTER TABLE plans 
ADD CONSTRAINT plans_code_unique UNIQUE (code);

-- Add NOT NULL constraint after populating all values
ALTER TABLE plans 
ALTER COLUMN code SET NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN plans.code IS 'Unique stable identifier for billing plans (e.g., STANDARD, PREMIUM). Used for plan lookups and versioning.';
