alter table guilds
  alter column created_on type timestamp using created_on::timestamp;
