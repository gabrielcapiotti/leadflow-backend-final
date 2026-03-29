-- Fix subscription status constraint to include TRIALING and COMPLETED
-- V91 was missing TRIALING and COMPLETED from Java enum SubscriptionStatus
-- This caused ConstraintViolationException when creating trial subscriptions

ALTER TABLE public.subscriptions
DROP CONSTRAINT subscriptions_status_check;

ALTER TABLE public.subscriptions
ADD CONSTRAINT subscriptions_status_check 
CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'INCOMPLETE', 'TRIALING', 'COMPLETED'));
