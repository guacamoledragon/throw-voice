alter table guilds
  alter column created_on type timestamptz using created_on::timestamptz;
