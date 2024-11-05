create index idx_recordings_guild_id
  on RECORDINGS (GUILD, ID);

comment on index idx_recordings_guild_id is 'Compound index on guild and id.';
