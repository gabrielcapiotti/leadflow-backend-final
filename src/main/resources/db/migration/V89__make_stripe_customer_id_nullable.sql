/* ======================================================
   V89__make_stripe_customer_id_nullable.sql
   Make stripe_customer_id nullable to support deferred Stripe integration
   ====================================================== */

-- Allow subscriptions to be created locally before Stripe integration
-- Stripe customer ID will be populated during checkout/payment process
ALTER TABLE public.subscriptions
    ALTER COLUMN stripe_customer_id DROP NOT NULL;

-- Comment for documentation
COMMENT ON COLUMN public.subscriptions.stripe_customer_id 
    IS 'Stripe Customer ID - populated after Stripe integration completes. NULL until checkout is initiated.';
