alter table guilds
alter column last_active_on type timestamp using last_active_on::timestamp;
