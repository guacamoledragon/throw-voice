-- Phase 3: Remove prefix command support
-- The aliases table only served prefix commands. Drop it first (FK to settings).
DROP TABLE IF EXISTS aliases;
