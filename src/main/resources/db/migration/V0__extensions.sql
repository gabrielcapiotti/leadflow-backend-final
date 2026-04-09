/* ======================================================
   V0__extensions.sql

   PostgreSQL extensions required by the system.

   Responsabilidades:
   - Garantir que extensões críticas existam
   - Falhar automaticamente em caso de erro de permissão
   - Manter execução simples e determinística

   Observações:
   - Deve rodar antes de qualquer migration que use UUID
   - Deve estar em /db/migration (NÃO em /tenant)
   ====================================================== */

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;