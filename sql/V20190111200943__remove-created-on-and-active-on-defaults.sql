create table Guilds_dg_tmp
(
  id             INTEGER
    primary key,
  name           TEXT not null,
  region         TEXT,
  created_on     text,
  last_active_on text
);

insert into Guilds_dg_tmp(id, name, region, created_on, last_active_on)
select id, name, region, created_on, last_active_on
from Guilds;

drop table Guilds;

alter table Guilds_dg_tmp
  rename to Guilds;
