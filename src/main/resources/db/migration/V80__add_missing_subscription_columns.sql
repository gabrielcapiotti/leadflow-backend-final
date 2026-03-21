-- Add missing columns to subscriptions table
ALTER TABLE public.subscriptions
ADD COLUMN IF NOT EXISTS last_payment_date TIMESTAMP,
ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
