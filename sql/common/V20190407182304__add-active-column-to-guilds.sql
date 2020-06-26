alter table Guilds
  add active INTEGER default 1;

update Guilds
set active = 0
where created_on = '2019-01-01 00:00:00'
  and last_active_on = '2019-01-01 00:00:00';
