create table Guilds_dg_tmp
(
  id             INTEGER
    primary key,
  name           TEXT not null,
  region         TEXT,
  created_on     TEXT,
  last_active_on TEXT,
  active         BOOLEAN default 1
);

insert into Guilds_dg_tmp(id, name, region, created_on, last_active_on, active)
select id, name, region, created_on, last_active_on, active
from Guilds;

drop table Guilds;

alter table Guilds_dg_tmp
  rename to Guilds;
