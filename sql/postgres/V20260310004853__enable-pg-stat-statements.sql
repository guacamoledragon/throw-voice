-- Requires shared_preload_libraries = 'pg_stat_statements' in postgresql.conf (or docker command args).
-- See docker-compose.yml database service command.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
