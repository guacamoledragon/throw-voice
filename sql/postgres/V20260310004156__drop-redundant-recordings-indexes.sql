-- recordings_id_unique is redundant with recordings_pkey (both enforce uniqueness on id)
DROP INDEX IF EXISTS recordings_id_unique;

-- idx_recordings_guild is covered by idx_recordings_guild_id_prefix (guild, id text_pattern_ops)
DROP INDEX IF EXISTS idx_recordings_guild;
