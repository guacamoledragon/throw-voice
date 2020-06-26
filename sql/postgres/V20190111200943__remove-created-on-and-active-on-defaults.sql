alter table Guilds
  alter column created_on drop default;

alter table Guilds
  alter column last_active_on drop default;
