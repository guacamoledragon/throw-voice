-- Remove all channel rows that autoRecord = null and autoStop = 1
delete
from Channels
where autoStop is 1
  and autoRecord isnull;

-- Reset autoStop to null to prevent accidental leaves
update Channels
set autoStop = null
where autoStop is 1;

--  Allow autostop to be null, just like autorecord
create table Channels_dg_tmp
(
  id         INTEGER
    primary key,
  name       TEXT   not null,
  autoRecord INT,
  autoStop   INT,
  settings   BIGINT not null
    references Settings
      on delete cascade
);

insert into Channels_dg_tmp(id, name, autoRecord, autoStop, settings)
select id, name, autoRecord, autoStop, settings
from Channels;

drop table Channels;

alter table Channels_dg_tmp
  rename to Channels;

