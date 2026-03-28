-- Align subscriptions.status constraint with Java enum SubscriptionStatus
-- Enum values: ACTIVE, PAST_DUE, CANCELLED, INCOMPLETE

ALTER TABLE public.subscriptions
ADD CONSTRAINT subscriptions_status_check 
CHECK (status IN ('ACTIVE','PAST_DUE','CANCELLED','INCOMPLETE'));
