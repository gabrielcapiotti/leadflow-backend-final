/* ======================================================
   V95__fix_settings_whatsapp_length.sql
   ====================================================== */

-- Direct: Alter column type
ALTER TABLE public.settings
ALTER COLUMN whatsapp TYPE VARCHAR(20);

COMMENT ON COLUMN public.settings.whatsapp
IS 'WhatsApp number - supports formatting (+, spaces, hyphens), up to 20 characters';