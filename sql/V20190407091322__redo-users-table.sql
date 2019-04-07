drop table Users;

create table Users
(
  id        INTEGER primary key,
  name      TEXT not null,
  voted_on  TEXT,
  supporter TEXT
);

create table Guilds_dg_tmp
(
  id             INTEGER
    primary key,
  name           TEXT not null,
  region         TEXT,
  created_on     TEXT,
  last_active_on TEXT,
  activated_by   INTEGER
    constraint Guilds_Users_id_fk
      references Users
);

insert into Guilds_dg_tmp(id, name, region, created_on, last_active_on)
select id, name, region, created_on, last_active_on
from Guilds;

drop table Guilds;

alter table Guilds_dg_tmp
  rename to Guilds;
