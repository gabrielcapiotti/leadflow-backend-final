/* ======================================================
   V58__normalize_vendor_leads_stage.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

UPDATE public.vendor_leads
SET stage = UPPER(stage)
WHERE stage IS NOT NULL
AND stage <> UPPER(stage);