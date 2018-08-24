create table Recordings
(
  id          INTEGER primary key,
  channel     INTEGER not null
    references Channels,
  size        INTEGER,
  created_on  TEXT   not null,
  modified_on TEXT,
  url         TEXT,
  guild       BIGINT not null
    references Guilds
      on delete cascade
);

create unique index Recordings_id_unique
  on Recordings (id)
