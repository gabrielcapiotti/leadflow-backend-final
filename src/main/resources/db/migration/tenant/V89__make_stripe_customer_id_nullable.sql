/* ======================================================
   V89__make_stripe_customer_id_nullable.sql
   ====================================================== */

-- Direct: Drop NOT NULL constraint
ALTER TABLE public.subscriptions
ALTER COLUMN stripe_customer_id DROP NOT NULL;

COMMENT ON COLUMN public.subscriptions.stripe_customer_id 
IS 'Stripe Customer ID - populated after Stripe integration completes. NULL until checkout is initiated.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_stripe_customer_id
ON public.subscriptions (stripe_customer_id)
WHERE stripe_customer_id IS NOT NULL;