alter table guilds
  add column unjoined_on timestamp with time zone;

comment on column guilds.unjoined_on is 'Guild stopped using Pawa.';

update
  guilds
set unjoined_on = last_active_on
where active = false;
