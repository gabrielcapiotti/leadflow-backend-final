/* ======================================================
   FIX SETTINGS WHATSAPP COLUMN LENGTH
   Issue: VARCHAR(15) was too short for WhatsApp numbers
   Example: "+55 11 9999-9999" = 16 characters
   Solution: Change to VARCHAR(20) to accommodate formatted numbers
   ====================================================== */

ALTER TABLE public.settings
ALTER COLUMN whatsapp TYPE VARCHAR(20);

COMMENT ON COLUMN public.settings.whatsapp IS 'WhatsApp number - can contain formatting (+, spaces, hyphens) up to 20 chars';
