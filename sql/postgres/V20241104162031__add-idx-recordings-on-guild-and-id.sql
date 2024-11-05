create index idx_recordings_guild_id_prefix
  on public.recordings (guild, id text_pattern_ops);

comment on index public.idx_recordings_guild_id_prefix is 'Compound index on guild and id';
