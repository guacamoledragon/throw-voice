-- Phase 3: Remove prefix command support
-- The prefix column only served prefix commands.
ALTER TABLE SETTINGS DROP COLUMN IF EXISTS PREFIX;
