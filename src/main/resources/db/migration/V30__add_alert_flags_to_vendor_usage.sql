/* ======================================================
   V32__add_usage_alert_flags.sql
   TENANT SCHEMA
   ====================================================== */

ALTER TABLE vendor_usage
ADD COLUMN alert80_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE vendor_usage
ADD COLUMN alert100_sent BOOLEAN NOT NULL DEFAULT FALSE;