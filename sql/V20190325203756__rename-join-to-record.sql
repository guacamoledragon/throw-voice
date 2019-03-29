create table Channels_dg_tmp
(
  id         INTEGER
    primary key,
  name       TEXT   not null,
  autoRecord INT,
  autoLeave  INT default 1 not null,
  settings   BIGINT not null
    references Settings
      on delete cascade
);

insert into Channels_dg_tmp(id, name, autoRecord, autoLeave, settings)
select id, name, autoJoin, autoLeave, settings
from Channels;

drop table Channels;

alter table Channels_dg_tmp
  rename to Channels;
