alter table guilds
alter column last_active_on type timestamptz using last_active_on::timestamptz;
